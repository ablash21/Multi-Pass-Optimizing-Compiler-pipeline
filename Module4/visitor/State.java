package visitor;
import java.util.*;

/**
 * Abstract program state at a program point.
 *
 *   P : Var -> Set<AbsObj>                      (the abstract stack)
 *   sigma : (AbsObj.siteLabel, FieldName) -> Set<AbsObj>   (the abstract heap)
 *
 * The two are kept separate because their semantics are different.  P is
 * method-local; sigma is global (the heap) and crosses call boundaries.
 *
 * All operations are monotone — copy(), joinFrom() never shrink any set —
 * which is what guarantees the fixpoint loops terminate.
 */
public class State {

    /** P : variable name -> points-to set */
    public final Map<String, Set<AbsObj>> stack = new HashMap<>();

    /** sigma : siteLabel -> field name -> points-to set */
    public final Map<String, Map<String, Set<AbsObj>>> heap = new HashMap<>();

    /* ------------------------------------------------------------------ */
    /*  Reading                                                            */
    /* ------------------------------------------------------------------ */

    /** P(v).  Returns empty set if missing — never null, never throws. */
    public Set<AbsObj> pts(String v) {
        if (v == null) return Collections.emptySet();
        Set<AbsObj> s = stack.get(v);
        return (s == null) ? Collections.emptySet() : s;
    }

    /** sigma(o, f). */
    public Set<AbsObj> heapGet(String siteLabel, String field) {
        Map<String, Set<AbsObj>> m = heap.get(siteLabel);
        if (m == null) return Collections.emptySet();
        Set<AbsObj> s = m.get(field);
        return (s == null) ? Collections.emptySet() : s;
    }

    /* ------------------------------------------------------------------ */
    /*  Writing                                                            */
    /* ------------------------------------------------------------------ */

    /** P(v) ∪= add */
    public void ptsAddAll(String v, Set<AbsObj> add) {
        if (v == null || add.isEmpty()) return;
        stack.computeIfAbsent(v, k -> new HashSet<>()).addAll(add);
    }

    /** P(v) = pts (overwrite — strong assignment). */
    public void ptsSet(String v, Set<AbsObj> pts) {
        if (v == null) return;
        stack.put(v, new HashSet<>(pts));
    }

    /** sigma(o, f) ∪= add  (weak update). */
    public void heapAddAll(String siteLabel, String field, Set<AbsObj> add) {
        if (add.isEmpty()) return;
        heap.computeIfAbsent(siteLabel, k -> new HashMap<>())
            .computeIfAbsent(field, k -> new HashSet<>())
            .addAll(add);
    }

    /** sigma(o, f) = pts  (strong update — only safe when |P(base)| == 1). */
    public void heapSet(String siteLabel, String field, Set<AbsObj> pts) {
        heap.computeIfAbsent(siteLabel, k -> new HashMap<>())
            .put(field, new HashSet<>(pts));
    }

    /** Make sure sigma(siteLabel, *) entries exist for every field of cls. */
    public void initHeapForObject(String siteLabel, String cls) {
        Map<String, Set<AbsObj>> obj = heap.computeIfAbsent(siteLabel, k -> new HashMap<>());
        Map<String, var_data> fields = SymbolTable.getAllFields(cls);
        for (String f : fields.keySet()) {
            obj.computeIfAbsent(f, k -> new HashSet<>());
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Copy / Join / Equality                                             */
    /* ------------------------------------------------------------------ */

    public State copy() {
        State s = new State();
        for (Map.Entry<String, Set<AbsObj>> e : stack.entrySet()) {
            s.stack.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        for (Map.Entry<String, Map<String, Set<AbsObj>>> oe : heap.entrySet()) {
            Map<String, Set<AbsObj>> inner = new HashMap<>();
            for (Map.Entry<String, Set<AbsObj>> fe : oe.getValue().entrySet()) {
                inner.put(fe.getKey(), new HashSet<>(fe.getValue()));
            }
            s.heap.put(oe.getKey(), inner);
        }
        return s;
    }

    /**
     * Union `other` into `this`.  Returns true iff `this` actually grew —
     * the caller uses this to decide whether to re-enqueue work.
     */
    public boolean joinFrom(State other) {
        boolean changed = false;
        for (Map.Entry<String, Set<AbsObj>> e : other.stack.entrySet()) {
            Set<AbsObj> mine = stack.computeIfAbsent(e.getKey(), k -> new HashSet<>());
            if (mine.addAll(e.getValue())) changed = true;
        }
        for (Map.Entry<String, Map<String, Set<AbsObj>>> oe : other.heap.entrySet()) {
            Map<String, Set<AbsObj>> myObj =
                heap.computeIfAbsent(oe.getKey(), k -> new HashMap<>());
            for (Map.Entry<String, Set<AbsObj>> fe : oe.getValue().entrySet()) {
                Set<AbsObj> mineF = myObj.computeIfAbsent(fe.getKey(), k -> new HashSet<>());
                if (mineF.addAll(fe.getValue())) changed = true;
            }
        }
        return changed;
    }

    /** Union just the heap portion (used when pulling a callee summary back). */
    public boolean joinHeapFrom(Map<String, Map<String, Set<AbsObj>>> otherHeap) {
        boolean changed = false;
        for (Map.Entry<String, Map<String, Set<AbsObj>>> oe : otherHeap.entrySet()) {
            Map<String, Set<AbsObj>> myObj =
                heap.computeIfAbsent(oe.getKey(), k -> new HashMap<>());
            for (Map.Entry<String, Set<AbsObj>> fe : oe.getValue().entrySet()) {
                Set<AbsObj> mineF = myObj.computeIfAbsent(fe.getKey(), k -> new HashSet<>());
                if (mineF.addAll(fe.getValue())) changed = true;
            }
        }
        return changed;
    }

    /**
     * Become the join of s1 and s2.  Used at if-else merge points where we
     * have two branch-exit states and want this to become their lub.
     */
    public void becomeJoin(State s1, State s2) {
        stack.clear();
        heap.clear();
        joinFrom(s1);
        joinFrom(s2);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof State)) return false;
        State s = (State) o;
        return stack.equals(s.stack) && heap.equals(s.heap);
    }

    @Override
    public int hashCode() { return stack.hashCode() ^ heap.hashCode(); }
}
