package visitor;
import syntaxtree.*;
import java.util.*;

/**
 * Pass 2 — IRBuilderVisitor.
 *
 * Walks the AST and populates {@code Method_data.ir} for every method,
 * including {@code MainClass.main}.  Produces a compact, points-to-relevant IR:
 *
 *   AllocStmt   x = new Foo()
 *   CopyStmt    x = y                (also: x = this)
 *   LoadStmt    x = y.f              (RhsExpression is DotExpression)
 *   StoreStmt   x.f = y              (FieldAssignmentStatement)
 *   CallStmt    (lhs)? = recv.m(args)  (MessageSendStatement, INLINE tracked)
 *   ReturnStmt  return v             (always emitted; engine ignores if non-object)
 *   IfStmt      wraps two sub-lists
 *   WhileStmt   wraps one sub-list
 *
 * Everything else — arithmetic, array ops, println, primitive constant assigns,
 * VarDeclaration — is omitted entirely.  Pass 3 has less to walk and fewer
 * irrelevant cases to skip.
 *
 *  R = String   — bubbles a variable name / "this" up from Identifier and
 *                 ThisExpression and PrimaryExpression visits.  Used to fill
 *                 CallStmt.args and to read the receiver of a call.
 *  A = String   — "ClassName.methodName" context flowing down so we know which
 *                 method's IR list to append to.  Unused inside expression
 *                 visits.
 *
 * Mutable state:
 *   stmtStack      — top is the List<IrStmt> currently being appended to.
 *                    Pushed/popped at method, if-branch, while-body boundaries.
 *   callSiteCounter— monotonic id assigned to every MessageSendStatement.
 *   nodeToSite     — side-output map.  Lets Pass 4 ask
 *                    "decisions.get(node)" by AST-node identity.
 */
public class Pass2 extends GJDepthFirst<String, String> {

    private final Deque<List<IrStmt>> stmtStack = new ArrayDeque<>();
    private int callSiteCounter = 0;

    /** Side-output for Pass 4 — keyed by AST node identity (not equals). */
    public final Map<MessageSendStatement, Integer> nodeToSite = new IdentityHashMap<>();

    /* =================================================================== */
    /*  Convenience: append to the current top-of-stack list                */
    /* =================================================================== */
    private void emit(IrStmt s) {
        stmtStack.peek().add(s);
    }

    /* =================================================================== */
    /*  Goal / MainClass / class decls                                      */
    /* =================================================================== */

    /**
     * f0 -> MainClass()
     * f1 -> ( TypeDeclaration() )*
     */
    public String visit(Goal n, String argu) {
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        return null;
    }

    /**
     * Build IR for "MainClass.main".  Pass 1 already registered this method.
     *
     * f1  -> Identifier()              class name
     * f15 -> ( Statement() )*          body
     *
     * (No return — main has no explicit return in the grammar.)
     */
    public String visit(MainClass n, String argu) {
        String cls = n.f1.f0.tokenImage;
        String key = cls + ".main";

        Method_data md = SymbolTable.getMethod(cls, "main");
        if (md == null) return null;          // defensive; Pass 1 should have made it

        // Fresh list, push as the active append target, walk statements.
        List<IrStmt> list = new ArrayList<>();
        stmtStack.push(list);
        n.f15.accept(this, key);
        stmtStack.pop();

        md.ir = list;
        return null;
    }

    /**
     * f0 -> ClassDeclaration() | ClassExtendsDeclaration()
     */
    public String visit(TypeDeclaration n, String argu) {
        n.f0.accept(this, argu);
        return null;
    }

    /**
     * f1 -> Identifier()
     * f4 -> ( MethodDeclaration() )*    (fields f3 are skipped — Pass 1 handled)
     */
    public String visit(ClassDeclaration n, String argu) {
        String cls = n.f1.f0.tokenImage;
        n.f4.accept(this, cls);
        return null;
    }

    /**
     * f1 -> Identifier()
     * f6 -> ( MethodDeclaration() )*
     */
    public String visit(ClassExtendsDeclaration n, String argu) {
        String cls = n.f1.f0.tokenImage;
        n.f6.accept(this, cls);
        return null;
    }

    /* =================================================================== */
    /*  Method                                                              */
    /* =================================================================== */

    /**
     * f2  -> Identifier()                       method name
     * f8  -> ( Statement() )*                   body
     * f10 -> ConstOrId()                        return expression
     *
     * Entering A is "ClassName".
     */
    public String visit(MethodDeclaration n, String argu) {
        String cls   = argu;
        String mname = n.f2.f0.tokenImage;
        String key   = cls + "." + mname;

        Method_data md = SymbolTable.getMethod(cls, mname);
        if (md == null) return null;            // defensive

        List<IrStmt> list = new ArrayList<>();
        stmtStack.push(list);

        // Body statements
        n.f8.accept(this, key);

        // Return — always emit, even for primitive returns.  The engine will
        // simply find no pts for an int variable and accumulate ∅.
        String retVar = n.f10.accept(this, key);    // null if literal
        list.add(new ReturnStmt(retVar));

        stmtStack.pop();
        md.ir = list;
        return null;
    }

    /* =================================================================== */
    /*  Statements                                                          */
    /* =================================================================== */

    /**
     * Statement is a NodeChoice — JTB's base visitor dispatches to the chosen
     * concrete type, which we override below.
     */
    public String visit(Statement n, String argu) {
        n.f0.accept(this, argu);
        return null;
    }

    /**
     * Block: statements just keep appending to the current list.  No push/pop —
     * blocks don't introduce a new IR-level scope.
     *
     * f1 -> ( Statement() )*
     */
    public String visit(Block n, String argu) {
        n.f1.accept(this, argu);
        return null;
    }

    /**
     * AssignmentStatement ::= Identifier "=" RhsExpression ";"
     *
     * RHS classification (matches transfer functions in spec):
     *   DotExpression                                    -> LoadStmt
     *   Expression -> PrimaryExpression -> AllocationExpr  -> AllocStmt
     *   Expression -> PrimaryExpression -> Identifier      -> CopyStmt
     *   Expression -> PrimaryExpression -> ThisExpression  -> CopyStmt(_, "this")
     *   anything else (arithmetic, literals, !x, arr op)   -> omit
     */
    public String visit(AssignmentStatement n, String argu) {
        String lhs = n.f0.f0.tokenImage;

        // RhsExpression is a NodeChoice between DotExpression (which == 0) and
        // Expression (which == 1).
        int which = n.f2.f0.which;
        if (which == 0) {
            DotExpression de = (DotExpression) n.f2.f0.choice;
            String base  = de.f0.f0.tokenImage;
            String field = de.f2.f0.tokenImage;
            emit(new LoadStmt(lhs, base, field));
            return null;
        }

        // which == 1 : Expression
        Expression expr = (Expression) n.f2.f0.choice;
        int eWhich = expr.f0.which;
        // 7 == PrimaryExpression (per grammar order).  Everything else is
        // arithmetic / comparison / array — omit.
        if (eWhich != 7) {
            return null;
        }

        PrimaryExpression pe = (PrimaryExpression) expr.f0.choice;
        int pWhich = pe.f0.which;
        switch (pWhich) {
            case 3: {  // Identifier
                String rhs = ((Identifier) pe.f0.choice).f0.tokenImage;
                emit(new CopyStmt(lhs, rhs));
                break;
            }
            case 4: {  // ThisExpression
                emit(new CopyStmt(lhs, "this"));
                break;
            }
            case 6: {  // AllocationExpression  ("new" Identifier "(" ")")
                AllocationExpression ae = (AllocationExpression) pe.f0.choice;
                String clsName = ae.f1.f0.tokenImage;
                int line = ae.f0.beginLine;     // line of the "new" token
                String site = clsName + "@" + line;
                emit(new AllocStmt(lhs, clsName, site));
                break;
            }
            // 0 IntegerLiteral, 1 TrueLiteral, 2 FalseLiteral,
            // 5 ArrayAllocationExpression, 7 NotExpression: omit (no object flow)
            default:
                break;
        }
        return null;
    }

    /**
     * FieldAssignmentStatement ::= Identifier "." Identifier "=" ConstOrId ";"
     *  → StoreStmt(base, field, rhs)
     *
     * If the RHS is a literal (ConstOrId returns null), still emit with rhs=null.
     * The engine's lookup of P(null) yields ∅ → store has no effect.  Cleaner
     * than special-casing literal RHS.
     */
    public String visit(FieldAssignmentStatement n, String argu) {
        String base  = n.f0.f0.tokenImage;
        String field = n.f2.f0.tokenImage;
        String rhs   = n.f4.accept(this, argu);     // may be null for literal
        emit(new StoreStmt(base, field, rhs));
        return null;
    }

    /**
     * IfStatement: build separate sub-lists for then and else, wrap in IfStmt.
     *
     * f4 -> Statement()  (then)
     * f6 -> Statement()  (else)
     */
    public String visit(IfStatement n, String argu) {
        List<IrStmt> thenList = new ArrayList<>();
        List<IrStmt> elseList = new ArrayList<>();

        stmtStack.push(thenList);
        n.f4.accept(this, argu);
        stmtStack.pop();

        stmtStack.push(elseList);
        n.f6.accept(this, argu);
        stmtStack.pop();

        emit(new IfStmt(thenList, elseList));
        return null;
    }

    /**
     * WhileStatement: build body sub-list, wrap in WhileStmt.
     *
     * f4 -> Statement()
     */
    public String visit(WhileStatement n, String argu) {
        List<IrStmt> body = new ArrayList<>();
        stmtStack.push(body);
        n.f4.accept(this, argu);
        stmtStack.pop();
        emit(new WhileStmt(body));
        return null;
    }

    /**
     * ForStatement lowers to WhileStmt: init and update are int arithmetic in
     * this grammar (Expression, not RhsExpression — no allocation), so omit
     * them entirely.  Only the body is wrapped.
     *
     * f12 -> Statement()  (body)
     */
    public String visit(ForStatement n, String argu) {
        List<IrStmt> body = new ArrayList<>();
        stmtStack.push(body);
        n.f12.accept(this, argu);
        stmtStack.pop();
        emit(new WhileStmt(body));
        return null;
    }

    /**
     * PrintStatement, ArrayAssignmentStatement — explicit no-op overrides so
     * default recursion doesn't accidentally descend into expressions and
     * cause stray emits.  Nothing in print/array touches points-to.
     */
    public String visit(PrintStatement n, String argu) { return null; }
    public String visit(ArrayAssignmentStatement n, String argu) { return null; }

    /* =================================================================== */
    /*  Message sends — the heart of inter-procedural analysis              */
    /* =================================================================== */

    /**
     * MessageSendStatement ::= ( InlineAnn )? ( VoidMessageSendStmt | RetMessageSendStmt )
     *
     * Records:
     *   - nodeToSite[n] = freshSiteId   (lets Pass 4 retrieve decisions by node)
     *   - CallStmt.siteId = same id     (lets Pass 3 key recvPtsAtCall)
     *   - CallStmt.astNode = n          (lets Pass 4 reconstruct the call shape)
     *   - CallStmt.annotated = whether /* INLINE *\/ was present
     */
    public String visit(MessageSendStatement n, String argu) {
        int siteId = callSiteCounter++;
        nodeToSite.put(n, siteId);

        boolean annotated = n.f0.present();

        // f1 is a NodeChoice between VoidMessageSendStmt (which==0)
        // and RetMessageSendStmt (which==1).
        Node child = n.f1.choice;

        String lhs;
        MessageSend ms;
        if (child instanceof VoidMessageSendStmt) {
            lhs = null;
            ms  = ((VoidMessageSendStmt) child).f0;
        } else {
            RetMessageSendStmt rs = (RetMessageSendStmt) child;
            lhs = rs.f0.f0.tokenImage;
            ms  = rs.f2;
        }

        // Receiver: PrimaryExpression.  In FunkyTACoJava the receiver of a call
        // is realistically an Identifier or `this` (anything else would be a
        // type error).  visit(PrimaryExpression) bubbles up the appropriate
        // name; if it's something exotic we just record the string we got.
        String receiver = ms.f0.accept(this, argu);

        // Args: walk ArgList if present.
        List<String> args = new ArrayList<>();
        if (ms.f4.present()) {
            ArgList al = (ArgList) ms.f4.node;
            args.add(al.f0.accept(this, argu));         // first arg
            for (Node rest : al.f1.nodes) {
                ArgRest ar = (ArgRest) rest;
                args.add(ar.f1.accept(this, argu));     // subsequent args
            }
        }

        String method = ms.f2.f0.tokenImage;
        emit(new CallStmt(lhs, receiver, method, args, annotated, siteId, n));
        return null;
    }

    /* =================================================================== */
    /*  Leaf-ish nodes that bubble up names                                 */
    /* =================================================================== */

    /**
     * ConstOrId ::= IntegerLiteral | Identifier | TrueLiteral | FalseLiteral
     *
     * Return the variable name only for the Identifier alternative; literals
     * return null so the engine's pts-lookup yields ∅.
     */
    public String visit(ConstOrId n, String argu) {
        int which = n.f0.which;
        if (which == 1) {   // Identifier
            return ((Identifier) n.f0.choice).f0.tokenImage;
        }
        return null;        // integer / true / false literal
    }

    /**
     * PrimaryExpression — used as receiver of a MessageSend.  Only meaningful
     * alternatives at a call receiver position:
     *   Identifier      -> name
     *   ThisExpression  -> "this"
     * Anything else returns null (not expected for a well-typed call).
     */
    public String visit(PrimaryExpression n, String argu) {
        int which = n.f0.which;
        if (which == 3) {   // Identifier
            return ((Identifier) n.f0.choice).f0.tokenImage;
        }
        if (which == 4) {   // ThisExpression
            return "this";
        }
        return null;
    }

    public String visit(Identifier n, String argu) {
        return n.f0.tokenImage;
    }

    public String visit(ThisExpression n, String argu) {
        return "this";
    }

    /* =================================================================== */
    /*  Debug dump — call after running Pass 2 to verify the IR             */
    /* =================================================================== */

    public static void dumpIR() {
        for (class_data cd : SymbolTable.class_map.values()) {
            for (Method_data md : cd.m_methods.values()) {
                System.err.println("=== " + cd.name + "." + md.name + " ===");
                dumpList(md.ir, 1);
            }
        }
    }

    private static void dumpList(List<IrStmt> stmts, int indent) {
        String pad = "  ".repeat(indent);
        for (IrStmt s : stmts) {
            if (s instanceof IfStmt) {
                System.err.println(pad + "IF");
                System.err.println(pad + "  THEN:");
                dumpList(((IfStmt) s).thenB, indent + 2);
                System.err.println(pad + "  ELSE:");
                dumpList(((IfStmt) s).elseB, indent + 2);
            } else if (s instanceof WhileStmt) {
                System.err.println(pad + "WHILE");
                dumpList(((WhileStmt) s).body, indent + 1);
            } else {
                System.err.println(pad + s.toString());
            }
        }
    }
}
