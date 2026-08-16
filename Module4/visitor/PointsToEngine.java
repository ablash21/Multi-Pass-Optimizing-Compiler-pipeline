package visitor;
import syntaxtree.MessageSendStatement;
import java.util.*;

/**
 * Pass 3 — Interprocedural points-to analysis + monomorphism + cycle filter.
 *
 * This is NOT a visitor.  It walks {@code Method_data.ir} (built by Pass 2).
 *
 * Architecture:
 *   Phase A — Worklist (outer fixpoint):
 *     Process (Method, Context) entries until none of their summaries change.
 *     For each method-context, run {@code analyze} on its IR — a recursive
 *     transfer-function evaluator with its own inner fixpoint for while loops.
 *     Side-effect: {@code recvPtsAtCall[siteId]} accumulates P(receiver) at
 *     every annotated CallStmt, monotonically unioned across iterations.
 *
 *   Phase B — Monomorphism (postprocess):
 *     For each annotated call site, ask: do all objects in recvPtsAtCall
 *     dispatch to the same method body?  If yes, provisional inline.
 *
 *   Phase C — Cycle filter (postprocess):
 *     Build the annotated-call subgraph from Phase B's provisional decisions.
 *     Find SCCs.  For any edge inside a non-trivial SCC, flip shouldInline=false.
 *     (Interpretation A — disable only cycle-closing edges, not the whole SCC.)
 */
public class PointsToEngine {

    /* ---- analysis state (Phase A) -------------------------------------- */

    private final Deque<MethodCtx> worklist = new ArrayDeque<>();
    private final Map<MethodCtx, State>   inState   = new HashMap<>();
    private final Map<MethodCtx, Summary> summaries = new HashMap<>();
    private final Map<MethodCtx, Set<MethodCtx>> callers = new HashMap<>();

    /** Per-call-site receiver pts, keyed by siteId, accumulated across contexts. */
    private final Map<Integer, Set<AbsObj>> recvPtsAtCall = new HashMap<>();

    /** Per-call-site: caller method (cls.method) at this site, for cycle filter. */
    private final Map<Integer, String> siteToCaller = new HashMap<>();

    /** Per-call-site: AST node, for the final decisions map keyed by node. */
    private final Map<Integer, MessageSendStatement> siteToAstNode = new HashMap<>();

    /** Per-call-site: the called method name, for monomorphism resolution. */
    private final Map<Integer, String> siteToMethodName = new HashMap<>();

    /** Set of annotated call siteIds seen anywhere in the program. */
    private final Set<Integer> annotatedSites = new HashSet<>();

    /* ---- final output -------------------------------------------------- */

    /** Pass 4's input.  Built in Phase B; mutated by Phase C cycle filter. */
    public final Map<MessageSendStatement, InlineDecision> decisions = new IdentityHashMap<>();

    /* ==================================================================== */
    /*  Entry point                                                         */
    /* ==================================================================== */

    public void run() {
        seedWorklist();

        // ---- Phase A: outer fixpoint via worklist ----
        while (!worklist.isEmpty()) {
            MethodCtx mk = worklist.poll();
            processMethodContext(mk);
        }

        // ---- Phase B: monomorphism check on annotated sites ----
        buildProvisionalDecisions();

        // ---- Phase C: cycle filter on annotated-call subgraph ----
        applyCycleFilter();
    }

    /* ==================================================================== */
    /*  Phase A — worklist                                                  */
    /* ==================================================================== */

    private void seedWorklist() {
        // 1) Seed main with an empty context (real program entry).
        for (class_data cd : SymbolTable.class_map.values()) {
            if (cd.m_methods.containsKey("main")) {
                MethodCtx mk = new MethodCtx(cd.name, "main", new Ctx());
                inState.put(mk, new State());
                worklist.add(mk);
                break;
            }
        }

        // 2) Seed every other method with a synthetic context so unreachable
        // methods still get analyzed.
        for (class_data cd : SymbolTable.class_map.values()) {
            for (Method_data md : cd.m_methods.values()) {
                if ("main".equals(md.name)) continue;
                Ctx synthCtx = buildSyntheticCtx(cd.name, md);
                MethodCtx mk = new MethodCtx(cd.name, md.name, synthCtx);
                if (!inState.containsKey(mk)) {
                    inState.put(mk, new State());
                    worklist.add(mk);
                }
            }
        }
    }

    private Ctx buildSyntheticCtx(String className, Method_data md) {
        Set<AbsObj> thisPts = new HashSet<>();
        thisPts.add(new AbsObj(className + "@SYNTH", className));

        Map<String, Set<AbsObj>> formalPts = new HashMap<>();
        for (String pname : md.arg_order) {
            var_data v = md.m_args.get(pname);
            String type = (v == null) ? null : v.data_type;
            if (SymbolTable.isClassType(type) && SymbolTable.class_map.containsKey(type)) {
                Set<AbsObj> s = new HashSet<>();
                s.add(new AbsObj(type + "@SYNTH", type));
                formalPts.put(pname, s);
            } else {
                formalPts.put(pname, new HashSet<>());
            }
        }

        return new Ctx(thisPts, formalPts);
    }

    private void processMethodContext(MethodCtx mk) {
        // Build the working state: copy of entry state + context-injected formals.
        State state = inState.get(mk).copy();
        state.ptsSet("this", mk.ctx.thisPts);
        for (Map.Entry<String, Set<AbsObj>> e : mk.ctx.formalPts.entrySet()) {
            state.ptsSet(e.getKey(), e.getValue());
        }

        Method_data md = SymbolTable.getMethod(mk.cls, mk.method);
        if (md == null) return;

        Set<AbsObj> returnPts = new HashSet<>();
        analyze(md.ir, state, returnPts, mk);

        // Outer-fixpoint test: did this method's summary change?
        Summary newSum = new Summary(returnPts, state.heap);
        Summary oldSum = summaries.get(mk);
        if (!newSum.equals(oldSum)) {
            summaries.put(mk, newSum);
            // Re-enqueue every caller — their downstream lhs/heap may now grow.
            for (MethodCtx caller : callers.getOrDefault(mk, Collections.emptySet())) {
                worklist.add(caller);
            }
        }
    }

    /* ==================================================================== */
    /*  Transfer function evaluator                                         */
    /* ==================================================================== */

    private void analyze(List<IrStmt> stmts, State state,
                         Set<AbsObj> returnPts, MethodCtx mk) {
        for (IrStmt s : stmts) {
            if (s instanceof AllocStmt) {
                AllocStmt a = (AllocStmt) s;
                AbsObj o = new AbsObj(a.siteLabel, a.className);
                Set<AbsObj> single = new HashSet<>();
                single.add(o);
                state.ptsSet(a.lhs, single);          // strong: fresh
                state.initHeapForObject(a.siteLabel, a.className);
            }
            else if (s instanceof CopyStmt) {
                CopyStmt c = (CopyStmt) s;
                // x = y  =>  P(x) = P(y)  (overwrite — `x` was newly written,
                // any prior value of x at this point is gone in this trace).
                state.ptsSet(c.lhs, state.pts(c.rhs));
            }
            else if (s instanceof LoadStmt) {
                LoadStmt l = (LoadStmt) s;
                Set<AbsObj> result = new HashSet<>();
                for (AbsObj o : state.pts(l.base)) {
                    result.addAll(state.heapGet(o.siteLabel, l.field));
                }
                state.ptsSet(l.lhs, result);
            }
            else if (s instanceof StoreStmt) {
                StoreStmt st = (StoreStmt) s;
                Set<AbsObj> bases = state.pts(st.base);
                Set<AbsObj> rhs   = state.pts(st.rhs);
                if (bases.size() == 1) {
                    // Strong update.  Safe: exactly one concrete heap location.
                    AbsObj only = bases.iterator().next();
                    state.heapSet(only.siteLabel, st.field, rhs);
                } else {
                    // Weak update.  Multiple aliases or none — must union.
                    for (AbsObj o : bases) {
                        state.heapAddAll(o.siteLabel, st.field, rhs);
                    }
                }
            }
            else if (s instanceof CallStmt) {
                handleCall((CallStmt) s, state, mk);
            }
            else if (s instanceof ReturnStmt) {
                ReturnStmt r = (ReturnStmt) s;
                returnPts.addAll(state.pts(r.var));
            }
            else if (s instanceof IfStmt) {
                IfStmt ifs = (IfStmt) s;
                State sThen = state.copy();
                State sElse = state.copy();
                analyze(ifs.thenB, sThen, returnPts, mk);
                analyze(ifs.elseB, sElse, returnPts, mk);
                state.becomeJoin(sThen, sElse);
            }
            else if (s instanceof WhileStmt) {
                WhileStmt ws = (WhileStmt) s;
                State prev;
                do {
                    prev = state.copy();
                    State after = state.copy();
                    analyze(ws.body, after, returnPts, mk);
                    state.joinFrom(after);
                } while (!state.equals(prev));
            }
            // NopStmt: skip
        }
    }

    /* ==================================================================== */
    /*  Call handling — implicit call-graph construction                    */
    /* ==================================================================== */

    private void handleCall(CallStmt cs, State callerState, MethodCtx caller) {
        // Bookkeeping for postprocessing (Phase B / C).
        if (cs.annotated) {
            annotatedSites.add(cs.siteId);
        }
        siteToCaller.putIfAbsent(cs.siteId, caller.cls + "." + caller.method);
        siteToAstNode.putIfAbsent(cs.siteId, cs.astNode);
        siteToMethodName.putIfAbsent(cs.siteId, cs.method);

        Set<AbsObj> recv = callerState.pts(cs.receiver);
        // Snapshot for monomorphism check (union across iterations/contexts).
        recvPtsAtCall.computeIfAbsent(cs.siteId, k -> new HashSet<>()).addAll(recv);

        // If receiver pts is empty, the call is currently unreachable — no
        // edges, no LHS update.  Subsequent iterations may grow it.
        if (recv.isEmpty()) return;

        Set<AbsObj> mergedReturnPts = new HashSet<>();

        for (AbsObj o : recv) {
            // Virtual dispatch: walk parent chain to find the defining class.
            String owner = SymbolTable.resolveMethod(o.cls, cs.method);
            if (owner == null) continue;   // method not found anywhere — skip
            Method_data target = SymbolTable.getMethod(o.cls, cs.method);
            if (target == null) continue;

            // Build the callee's context.
            Set<AbsObj> thisP = Collections.singleton(o);
            Map<String, Set<AbsObj>> formalP = new HashMap<>();
            for (int i = 0; i < target.arg_order.size() && i < cs.args.size(); i++) {
                String formal = target.arg_order.get(i);
                formalP.put(formal, new HashSet<>(callerState.pts(cs.args.get(i))));
            }
            Ctx calleeCtx = new Ctx(thisP, formalP);
            MethodCtx calleeKey = new MethodCtx(owner, cs.method, calleeCtx);

            // Track the caller→callee edge so summary changes can re-enqueue us.
            callers.computeIfAbsent(calleeKey, k -> new HashSet<>()).add(caller);

            // Propagate the heap into the callee's entry state.  The callee
            // sees the same sigma the caller sees right now.
            State propagated = callerState.copy();
            State existing = inState.get(calleeKey);
            if (existing == null) {
                inState.put(calleeKey, propagated);
                worklist.add(calleeKey);
            } else if (existing.joinFrom(propagated)) {
                worklist.add(calleeKey);
            }

            // Pull back the callee's exit info if we have a summary yet.
            Summary calleeSum = summaries.get(calleeKey);
            if (calleeSum != null) {
                mergedReturnPts.addAll(calleeSum.returnPts);
                callerState.joinHeapFrom(calleeSum.exitHeap);
            }
        }

        if (cs.lhs != null) {
            // LHS gets whatever any reachable callee can return.
            callerState.ptsSet(cs.lhs, mergedReturnPts);
        }
    }

    /* ==================================================================== */
    /*  Phase B — monomorphism check                                        */
    /* ==================================================================== */

    private void buildProvisionalDecisions() {
        for (int siteId : annotatedSites) {
            MessageSendStatement node = siteToAstNode.get(siteId);
            if (node == null) continue;

            Set<AbsObj> recv = recvPtsAtCall.getOrDefault(siteId, Collections.emptySet());
            String method   = siteToMethodName.get(siteId);

            InlineDecision d = new InlineDecision();

            if (recv.isEmpty() || method == null) {
                d.shouldInline = false;
                decisions.put(node, d);
                continue;
            }

            // Resolve every object's dispatched method.  Monomorphic iff the
            // resolved-targets set has exactly one element.
            Set<String> resolvedTargets = new HashSet<>();
            String anyOwner = null;
            for (AbsObj o : recv) {
                String owner = SymbolTable.resolveMethod(o.cls, method);
                if (owner == null) {
                    resolvedTargets.clear();           // give up; force polymorphic
                    resolvedTargets.add("__UNRESOLVED__");
                    resolvedTargets.add("__UNRESOLVED2__");
                    break;
                }
                resolvedTargets.add(owner + "." + method);
                anyOwner = owner;
            }

            if (resolvedTargets.size() == 1) {
                Method_data target = SymbolTable.getMethod(anyOwner, method);
                if (target == null) {
                    d.shouldInline = false;
                } else {
                    d.shouldInline = true;
                    d.targetClass = anyOwner;
                    d.targetMethod = method;
                }
            } else {
                d.shouldInline = false;
            }
            decisions.put(node, d);
        }
    }

    /* ==================================================================== */
    /*  Phase C — SCC-based cycle filter (Interpretation A)                 */
    /* ==================================================================== */

    /**
     * For every annotated edge `from -> to` (caller method -> callee method),
     * if both endpoints sit in the same non-trivial SCC, disable inlining.
     * This blocks infinite expansion without disabling safe non-cycle edges.
     */
    private void applyCycleFilter() {
        // Build annotated-edge graph from currently-shouldInline decisions.
        Map<String, Set<String>> edges = new HashMap<>();
        // Per edge string "from->to", remember which siteIds use it (to disable later).
        Map<String, List<Integer>> edgeSites = new HashMap<>();

        for (int siteId : annotatedSites) {
            MessageSendStatement node = siteToAstNode.get(siteId);
            if (node == null) continue;
            InlineDecision d = decisions.get(node);
            if (d == null || !d.shouldInline) continue;

            String from = siteToCaller.get(siteId);
            String to   = d.targetClass + "." + d.targetMethod;
            edges.computeIfAbsent(from, k -> new HashSet<>()).add(to);
            edgeSites.computeIfAbsent(from + "->" + to, k -> new ArrayList<>()).add(siteId);
        }

        // Compute SCCs (Tarjan's).
        List<Set<String>> sccs = tarjanSCC(edges);
        Map<String, Integer> nodeToScc = new HashMap<>();
        for (int i = 0; i < sccs.size(); i++) {
            for (String n : sccs.get(i)) nodeToScc.put(n, i);
        }

        // Disable any edge inside a non-trivial SCC (size > 1, or self-loop).
        for (Map.Entry<String, List<Integer>> e : edgeSites.entrySet()) {
            String[] parts = e.getKey().split("->", 2);
            String from = parts[0], to = parts[1];
            Integer sccU = nodeToScc.get(from);
            Integer sccV = nodeToScc.get(to);
            if (sccU == null || sccV == null) continue;
            if (!sccU.equals(sccV)) continue;
            boolean nonTrivial = sccs.get(sccU).size() > 1
                              || edges.getOrDefault(from, Collections.emptySet()).contains(from);
            if (!nonTrivial) continue;

            for (int siteId : e.getValue()) {
                MessageSendStatement node = siteToAstNode.get(siteId);
                if (node != null) {
                    InlineDecision d = decisions.get(node);
                    if (d != null) d.shouldInline = false;
                }
            }
        }
    }

    /* ---- Tarjan's SCC -------------------------------------------------- */

    private int tarjanIndex;
    private final Map<String, Integer> tIndex = new HashMap<>();
    private final Map<String, Integer> tLow   = new HashMap<>();
    private final Deque<String> tStack        = new ArrayDeque<>();
    private final Set<String> tOnStack        = new HashSet<>();
    private final List<Set<String>> tResult   = new ArrayList<>();

    private List<Set<String>> tarjanSCC(Map<String, Set<String>> edges) {
        tarjanIndex = 0;
        tIndex.clear(); tLow.clear(); tStack.clear(); tOnStack.clear(); tResult.clear();

        // Make sure every referenced node is considered, including pure sinks.
        Set<String> allNodes = new HashSet<>(edges.keySet());
        for (Set<String> dests : edges.values()) allNodes.addAll(dests);

        for (String v : allNodes) {
            if (!tIndex.containsKey(v)) tarjanVisit(v, edges);
        }
        return tResult;
    }

    private void tarjanVisit(String v, Map<String, Set<String>> edges) {
        tIndex.put(v, tarjanIndex);
        tLow.put(v, tarjanIndex);
        tarjanIndex++;
        tStack.push(v);
        tOnStack.add(v);

        for (String w : edges.getOrDefault(v, Collections.emptySet())) {
            if (!tIndex.containsKey(w)) {
                tarjanVisit(w, edges);
                tLow.put(v, Math.min(tLow.get(v), tLow.get(w)));
            } else if (tOnStack.contains(w)) {
                tLow.put(v, Math.min(tLow.get(v), tIndex.get(w)));
            }
        }

        if (tLow.get(v).equals(tIndex.get(v))) {
            Set<String> scc = new HashSet<>();
            String w;
            do {
                w = tStack.pop();
                tOnStack.remove(w);
                scc.add(w);
            } while (!w.equals(v));
            tResult.add(scc);
        }
    }

    /* ==================================================================== */
    /*  Debug                                                               */
    /* ==================================================================== */

    public void dump() {
        System.err.println("=== PointsToEngine summaries ===");
        for (Map.Entry<MethodCtx, Summary> e : summaries.entrySet()) {
            System.err.println(e.getKey() + " -> return=" + e.getValue().returnPts);
        }
        System.err.println("=== Decisions ===");
        for (Map.Entry<MessageSendStatement, InlineDecision> e : decisions.entrySet()) {
            System.err.println("call site -> " + e.getValue());
        }
    }
}
