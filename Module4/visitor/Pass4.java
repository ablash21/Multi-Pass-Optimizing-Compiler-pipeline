package visitor;
import syntaxtree.*;
import java.util.*;

/**
 * Pass 4 — PrettyPrinter + Inliner.
 *
 * Walks the AST and returns the transformed FunkyTACoJava source as a String.
 *
 *  R = String         — emitted source fragment for the visited node.
 *  A = RenameContext  — null in caller scope (faithful reprint);
 *                       non-null when emitting a callee body inline (identifiers
 *                       get suffixed and bare field refs rewritten through
 *                       this@N).  One RenameContext per inlined call site.
 *
 * Per-method state (reset on entering each MethodDeclaration / MainClass):
 *   hoistedDecls       — accumulated "Type name@N;" lines that must appear
 *                        before any statement of the caller method.
 *   currentClassName   — name of the class being walked.
 *   currentMethodName  — name of the method being walked.
 *
 * Global state:
 *   decisions    — from Pass 3.  Looked up by AST node identity.
 *   siteCounter  — strictly increasing; per Rule 2, never reused even if a
 *                  decision was "skip".
 *
 * Inlining transformation (per Rules 2–7):
 *   At an annotated, monomorphic, leaf MessageSendStatement:
 *     1) Allocate a fresh siteId N (always increment).
 *     2) Hoist declarations: "OwnerClass this@N;" + each formal/local with @N.
 *     3) Emit a Block:
 *          {
 *            this@N = receiver;
 *            param1@N = actual1; ...
 *            <renamed body statements>
 *          }
 *     4) After the block, emit "lhs = returnVar@N;" if call has a LHS.
 *   Field reads inside the body are rewritten as "this@N.field" (no lifting —
 *   avoids the read-after-write correctness issue).
 *   Annotation /* INLINE *\/ is dropped in output whether the call was inlined
 *   or not.
 */
public class Pass4 extends GJDepthFirst<String, RenameContext> {

    /* ------------------------------------------------------------------ */
    /*  Constants                                                          */
    /* ------------------------------------------------------------------ */

    /** Suffix-marker character used for renamed identifiers. */
    private static final String SUFFIX_PREFIX = "_";

    private static final String NL = "\n";

    /* ------------------------------------------------------------------ */
    /*  Configuration / shared state                                       */
    /* ------------------------------------------------------------------ */

    private final Map<MessageSendStatement, InlineDecision> decisions;

    /** Per Rule 2: monotonic counter, incremented on every inlineable call site
     *  (whether or not we actually inlined). */
    private int siteCounter = 0;

    /* ------------------------------------------------------------------ */
    /*  Per-method scratch (reset on MainClass / MethodDeclaration entry)  */
    /* ------------------------------------------------------------------ */

    private List<String> hoistedDecls = new ArrayList<>();
    private String currentClassName = null;
    private String currentMethodName = null;

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                            */
    /* ------------------------------------------------------------------ */

    public Pass4(Map<MessageSendStatement, InlineDecision> decisions) {
        this.decisions = decisions;
    }

    /** Emit a sub-node, threading the rename context through. */
    private String emit(Node n, RenameContext ctx) {
        return (n == null) ? "" : n.accept(this, ctx);
    }

    /** Apply rename rules (Rule 4) to a plain identifier seen in callee body. */
    private String renameIdent(String name, RenameContext ctx) {
        if (ctx == null) return name;
        if (ctx.localScope.contains(name)) return name + SUFFIX_PREFIX + suffixOf(ctx);
        if (ctx.fieldScope.contains(name)) {
            // bare field reference -> this@N.field
            return "this" + SUFFIX_PREFIX + suffixOf(ctx) + "." + name;
        }
        return name;
    }

    private String suffixOf(RenameContext ctx) {
        // ctx.suffix already includes the SUFFIX_PREFIX (e.g. "@1"); strip it
        // back to just the number so renameIdent can rebuild consistently.
        return ctx.suffix.startsWith(SUFFIX_PREFIX)
            ? ctx.suffix.substring(SUFFIX_PREFIX.length())
            : ctx.suffix;
    }

    private String resolveReceiverType(String receiver, Method_data target) {
        if (receiver == null || target == null) return (target == null) ? null : target.owner_class;
        if ("this".equals(receiver)) return currentClassName;

        Method_data cur = SymbolTable.getMethod(currentClassName, currentMethodName);
        if (cur != null) {
            var_data arg = cur.m_args.get(receiver);
            if (arg != null) return arg.data_type;
            var_data local = cur.m_vars.get(receiver);
            if (local != null) return local.data_type;
        }

        Map<String, var_data> fields = SymbolTable.getAllFields(currentClassName);
        var_data field = fields.get(receiver);
        if (field != null) return field.data_type;

        return target.owner_class;
    }

    /* =================================================================== */
    /*  Goal                                                                */
    /* =================================================================== */

    /**
     * f0 -> MainClass()
     * f1 -> ( TypeDeclaration() )*
     * f2 -> <EOF>
     */
    public String visit(Goal n, RenameContext argu) {
        StringBuilder sb = new StringBuilder();
        sb.append(emit(n.f0, argu));
        for (Node t : n.f1.nodes) sb.append(emit(t, argu));
        return sb.toString();
    }

    /* =================================================================== */
    /*  MainClass                                                           */
    /* =================================================================== */

    /**
     * Same emit pattern as a regular method: header, then original VarDecls,
     * then the hoisted decls (built while we walk statements), then the body
     * statement buffer.
     *
     * f1  -> Identifier()        class name
     * f11 -> Identifier()        args param
     * f14 -> ( VarDeclaration() )*
     * f15 -> ( Statement() )*
     */
    public String visit(MainClass n, RenameContext argu) {
        currentClassName = n.f1.f0.tokenImage;
        currentMethodName = "main";
        hoistedDecls = new ArrayList<>();

        // ---- original var decls ----
        StringBuilder origDecls = new StringBuilder();
        for (Node vd : n.f14.nodes) origDecls.append(emit(vd, null));

        // ---- body (may push to hoistedDecls) ----
        StringBuilder body = new StringBuilder();
        for (Node st : n.f15.nodes) body.append(emit(st, null));

        // ---- assemble ----
        StringBuilder sb = new StringBuilder();
        sb.append("class ").append(currentClassName).append(" {").append(NL);
        sb.append("  public static void main(String[] ");
        sb.append(n.f11.f0.tokenImage).append(") {").append(NL);
        sb.append(origDecls);
        for (String d : hoistedDecls) sb.append("    ").append(d).append(NL);
        sb.append(body);
        sb.append("  }").append(NL);
        sb.append("}").append(NL);
        return sb.toString();
    }

    /* =================================================================== */
    /*  Class declarations                                                  */
    /* =================================================================== */

    /**
     * f0 -> ClassDeclaration() | ClassExtendsDeclaration()
     */
    public String visit(TypeDeclaration n, RenameContext argu) {
        return emit(n.f0.choice, argu);
    }

    /**
     * f1 -> Identifier()
     * f3 -> ( VarDeclaration() )*  fields
     * f4 -> ( MethodDeclaration() )*
     */
    public String visit(ClassDeclaration n, RenameContext argu) {
        currentClassName = n.f1.f0.tokenImage;
        StringBuilder sb = new StringBuilder();
        sb.append("class ").append(currentClassName).append(" {").append(NL);
        for (Node f  : n.f3.nodes) sb.append("  ").append(emit(f, null));
        for (Node md : n.f4.nodes) sb.append(emit(md, null));
        sb.append("}").append(NL);
        return sb.toString();
    }

    /**
     * f1 -> Identifier()       class name
     * f3 -> Identifier()       parent
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     */
    public String visit(ClassExtendsDeclaration n, RenameContext argu) {
        currentClassName = n.f1.f0.tokenImage;
        String parent    = n.f3.f0.tokenImage;
        StringBuilder sb = new StringBuilder();
        sb.append("class ").append(currentClassName)
          .append(" extends ").append(parent).append(" {").append(NL);
        for (Node f  : n.f5.nodes) sb.append("  ").append(emit(f, null));
        for (Node md : n.f6.nodes) sb.append(emit(md, null));
        sb.append("}").append(NL);
        return sb.toString();
    }

    /* =================================================================== */
    /*  Var / Method declarations                                           */
    /* =================================================================== */

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    public String visit(VarDeclaration n, RenameContext argu) {
        return emit(n.f0, argu) + " " + n.f1.f0.tokenImage + ";" + NL;
    }

    /**
     * Top-level method.  Two-buffer pattern:
     *   - walk statements into a StringBuilder (this may push to hoistedDecls)
     *   - emit:  header + originalDecls + hoistedDecls + bodyBuf + return + close
     *
     * f1  -> Type()
     * f2  -> Identifier()                method name
     * f4  -> ( FormalParameterList() )?
     * f7  -> ( VarDeclaration() )*       locals
     * f8  -> ( Statement() )*            body
     * f10 -> ConstOrId()                 return expr
     */
    public String visit(MethodDeclaration n, RenameContext argu) {
        currentMethodName = n.f2.f0.tokenImage;
        hoistedDecls = new ArrayList<>();

        String retType = emit(n.f1, null);
        String params  = n.f4.present() ? emit(n.f4.node, null) : "";

        // Walk locals into one buffer, statements into another.
        StringBuilder origDecls = new StringBuilder();
        for (Node vd : n.f7.nodes) origDecls.append(emit(vd, null));

        StringBuilder body = new StringBuilder();
        for (Node st : n.f8.nodes) body.append(emit(st, null));

        // Return expression: also emit in caller scope (argu=null).
        String retExpr = emit(n.f10, null);

        StringBuilder sb = new StringBuilder();
        sb.append("  public ").append(retType).append(" ")
          .append(currentMethodName).append("(").append(params).append(") {")
          .append(NL);
        sb.append(origDecls);
        for (String d : hoistedDecls) sb.append("    ").append(d).append(NL);
        sb.append(body);
        sb.append("    return ").append(retExpr).append(";").append(NL);
        sb.append("  }").append(NL);
        return sb.toString();
    }

    /* =================================================================== */
    /*  Formals                                                             */
    /* =================================================================== */

    /**
     * f0 -> FormalParameter()
     * f1 -> ( FormalParameterRest() )*
     */
    public String visit(FormalParameterList n, RenameContext argu) {
        StringBuilder sb = new StringBuilder();
        sb.append(emit(n.f0, argu));
        for (Node r : n.f1.nodes) sb.append(emit(r, argu));
        return sb.toString();
    }

    public String visit(FormalParameter n, RenameContext argu) {
        return emit(n.f0, argu) + " " + n.f1.f0.tokenImage;
    }

    public String visit(FormalParameterRest n, RenameContext argu) {
        return ", " + emit(n.f1, argu);
    }

    /* =================================================================== */
    /*  Types                                                               */
    /* =================================================================== */

    public String visit(Type n, RenameContext argu)        { return emit(n.f0.choice, argu); }
    public String visit(ArrayType n, RenameContext argu)   { return "int[]"; }
    public String visit(BooleanType n, RenameContext argu) { return "boolean"; }
    public String visit(IntegerType n, RenameContext argu) { return "int"; }

    /* =================================================================== */
    /*  Statements                                                          */
    /* =================================================================== */

    public String visit(Statement n, RenameContext argu) { return emit(n.f0.choice, argu); }

    /**
     * f0 -> "{"
     * f1 -> ( Statement() )*
     * f2 -> "}"
     */
    public String visit(Block n, RenameContext argu) {
        StringBuilder sb = new StringBuilder();
        sb.append("    {").append(NL);
        for (Node st : n.f1.nodes) sb.append(emit(st, argu));
        sb.append("    }").append(NL);
        return sb.toString();
    }

    /**
     * AssignmentStatement ::= Identifier "=" RhsExpression ";"
     *
     * In callee scope (argu != null), the LHS identifier must also be renamed
     * according to Rule 4.
     */
    public String visit(AssignmentStatement n, RenameContext argu) {
        String lhs = renameIdent(n.f0.f0.tokenImage, argu);
        String rhs = emit(n.f2, argu);
        return "    " + lhs + " = " + rhs + ";" + NL;
    }

    /**
     * f0 -> DotExpression() | Expression()
     */
    public String visit(RhsExpression n, RenameContext argu) {
        return emit(n.f0.choice, argu);
    }

    /**
     * ArrayAssignmentStatement ::= Identifier "[" ConstOrId "]" "=" ConstOrId ";"
     */
    public String visit(ArrayAssignmentStatement n, RenameContext argu) {
        String base = renameIdent(n.f0.f0.tokenImage, argu);
        String idx  = emit(n.f2, argu);
        String rhs  = emit(n.f5, argu);
        return "    " + base + "[" + idx + "] = " + rhs + ";" + NL;
    }

    /**
     * FieldAssignmentStatement ::= Identifier "." Identifier "=" ConstOrId ";"
     *
     * Per Rule 5, in callee scope the base identifier needs careful handling:
     *   - "this"        -> "this@N"
     *   - local/formal  -> "name@N"     (object held in a local, write into it)
     *   - field of self -> "this@N.<base>"  (writing through self's field — rare,
     *                                        but consistent with renameIdent)
     */
    public String visit(FieldAssignmentStatement n, RenameContext argu) {
        String baseName = n.f0.f0.tokenImage;
        String base;
        if (argu != null && "this".equals(baseName)) {
            base = "this" + argu.suffix;
        } else {
            base = renameIdent(baseName, argu);
        }
        String field = n.f2.f0.tokenImage;     // never renamed
        String rhs   = emit(n.f4, argu);
        return "    " + base + "." + field + " = " + rhs + ";" + NL;
    }

    /**
     * IfStatement ::= "if" "(" Identifier ")" Statement "else" Statement
     */
    public String visit(IfStatement n, RenameContext argu) {
        String cond = renameIdent(n.f2.f0.tokenImage, argu);
        StringBuilder sb = new StringBuilder();
        sb.append("    if (").append(cond).append(")").append(NL);
        sb.append(emit(n.f4, argu));
        sb.append("    else").append(NL);
        sb.append(emit(n.f6, argu));
        return sb.toString();
    }

    /**
     * WhileStatement ::= "while" "(" Identifier ")" Statement
     */
    public String visit(WhileStatement n, RenameContext argu) {
        String cond = renameIdent(n.f2.f0.tokenImage, argu);
        StringBuilder sb = new StringBuilder();
        sb.append("    while (").append(cond).append(")").append(NL);
        sb.append(emit(n.f4, argu));
        return sb.toString();
    }

    /**
     * ForStatement ::= "for" "(" Identifier "=" Expression ";"
     *                              Expression ";"
     *                              Identifier "=" Expression ")" Statement
     */
    public String visit(ForStatement n, RenameContext argu) {
        String initVar  = renameIdent(n.f2.f0.tokenImage, argu);
        String initExpr = emit(n.f4, argu);
        String cond     = emit(n.f6, argu);
        String updVar   = renameIdent(n.f8.f0.tokenImage, argu);
        String updExpr  = emit(n.f10, argu);
        StringBuilder sb = new StringBuilder();
        sb.append("    for (").append(initVar).append(" = ").append(initExpr)
          .append("; ").append(cond).append("; ")
          .append(updVar).append(" = ").append(updExpr).append(")").append(NL);
        sb.append(emit(n.f12, argu));
        return sb.toString();
    }

    /**
     * PrintStatement ::= "System.out.println" "(" ConstOrId ")" ";"
     */
    public String visit(PrintStatement n, RenameContext argu) {
        return "    System.out.println(" + emit(n.f2, argu) + ");" + NL;
    }

    /* =================================================================== */
    /*  MessageSendStatement — THE inlining decision point                  */
    /* =================================================================== */

    /**
     * f0 -> ( InlineAnn() )?
     * f1 -> ( VoidMessageSendStmt() | RetMessageSendStmt() )
     *
     * Decision flow:
     *   - look up decisions.get(n).  If present and shouldInline -> inline.
    *   - otherwise emit a plain call, preserving the /* INLINE *\/ marker.
     *
     * Importantly: even when an inlined call appears *inside* a callee body
     * (argu != null), we should NOT recursively inline it.  Pass 3's safety
     * filter already refused to inline a call whose callee contains calls, so
     * in practice this case shouldn't reach inlining.  Defensive check anyway.
     */
    public String visit(MessageSendStatement n, RenameContext argu) {
        InlineDecision d = decisions.get(n);

        // Only top-level (caller scope) inlining; inside an already-inlined
        // body we just rewrite identifiers normally.
        if (argu == null && d != null && d.shouldInline) {
            return emitInlinedCall(n, d);
        }

        // Plain call — drop the annotation either way.
        return emitOriginalCall(n, argu, n.f0.present());
    }

    /* ---- emit original call (no inlining) ----------------------------- */

    private String emitOriginalCall(MessageSendStatement n, RenameContext argu, boolean annotated) {
        StringBuilder sb = new StringBuilder();
        if (annotated) {
            sb.append("    /* INLINE */").append(NL);
        }
        Node child = n.f1.choice;
        if (child instanceof VoidMessageSendStmt) {
            VoidMessageSendStmt v = (VoidMessageSendStmt) child;
            sb.append("    ").append(emit(v.f0, argu)).append(";").append(NL);
            return sb.toString();
        } else {
            RetMessageSendStmt r = (RetMessageSendStmt) child;
            String lhs = renameIdent(r.f0.f0.tokenImage, argu);
            sb.append("    ").append(lhs).append(" = ").append(emit(r.f2, argu))
              .append(";").append(NL);
            return sb.toString();
        }
    }

    /* ---- emit inlined call -------------------------------------------- */

    private String emitInlinedCall(MessageSendStatement n, InlineDecision d) {
        // Per Rule 2: always increment, even for skipped sites (we already
        // returned early for skipped ones, but the counter pattern is monotone).
        int siteId = ++siteCounter;
        String suffix = SUFFIX_PREFIX + siteId;

        Method_data target = SymbolTable.getMethod(d.targetClass, d.targetMethod);
        if (target == null) {
            // Defensive: Pass 3 said monomorphic but lookup fails — fall back.
            return emitOriginalCall(n, null, n.f0.present());
        }

        // ---- extract call shape ----
        Node child = n.f1.choice;
        String lhs;
        MessageSend ms;
        if (child instanceof VoidMessageSendStmt) {
            lhs = null;
            ms  = ((VoidMessageSendStmt) child).f0;
        } else {
            RetMessageSendStmt rs = (RetMessageSendStmt) child;
            lhs = rs.f0.f0.tokenImage;   // caller-scope, no rename
            ms  = rs.f2;
        }
        // Receiver: caller-scope identifier or "this".
        String receiver = emit(ms.f0, null);
        // Actuals: each is a caller-scope ConstOrId.
        List<String> actuals = new ArrayList<>();
        if (ms.f4.present()) {
            ArgList al = (ArgList) ms.f4.node;
            actuals.add(emit(al.f0, null));
            for (Node rest : al.f1.nodes) {
                ArgRest ar = (ArgRest) rest;
                actuals.add(emit(ar.f1, null));
            }
        }

        // ---- hoist declarations (Rule 6) ----
        String thisType = resolveReceiverType(receiver, target);
        hoistedDecls.add(thisType + " this" + suffix + ";");
        for (String p : target.arg_order) {
            var_data vd = target.m_args.get(p);
            hoistedDecls.add(vd.data_type + " " + p + suffix + ";");
        }
        for (var_data lv : target.m_vars.values()) {
            hoistedDecls.add(lv.data_type + " " + lv.name + suffix + ";");
        }

        // ---- build the inlined block (Rules 3, 4, 5, 7) ----
        RenameContext ctx = new RenameContext(target, suffix, thisType);
        StringBuilder out = new StringBuilder();

        out.append("    {").append(NL);

        // (1) receiver binding
        out.append("      this").append(suffix).append(" = ").append(receiver).append(";").append(NL);

        // (2) formal bindings in declaration order
        for (int i = 0; i < target.arg_order.size() && i < actuals.size(); i++) {
            out.append("      ").append(target.arg_order.get(i)).append(suffix)
               .append(" = ").append(actuals.get(i)).append(";").append(NL);
        }

        // (3) renamed body — walk the callee's statements with the rename ctx
        for (Node st : target.methodNode.f8.nodes) {
            out.append(emit(st, ctx));
        }

        out.append("    }").append(NL);

        // (5) return capture — outside the block (suffix names are hoisted to
        // caller scope so they're visible here).
        if (lhs != null) {
            String renamedRetExpr = emit(target.methodNode.f10, ctx);
            out.append("    ").append(lhs).append(" = ").append(renamedRetExpr)
               .append(";").append(NL);
        }
        return out.toString();
    }

    /* ---- the call-form children: only used outside inlining ---------- */

    /**
     * VoidMessageSendStmt ::= MessageSend ";"
     * Only reached for non-inlined calls (or calls in a callee body that we're
     * already emitting through a Renamer).
     */
    public String visit(VoidMessageSendStmt n, RenameContext argu) {
        return emit(n.f0, argu) + ";";
    }

    /**
     * RetMessageSendStmt ::= Identifier "=" MessageSend ";"
     */
    public String visit(RetMessageSendStmt n, RenameContext argu) {
        String lhs = renameIdent(n.f0.f0.tokenImage, argu);
        return lhs + " = " + emit(n.f2, argu) + ";";
    }

    /**
     * InlineAnn — we never emit this in the output, but the visitor needs
     * a method so traversal doesn't NPE in unusual code paths.
     */
    public String visit(InlineAnn n, RenameContext argu) { return ""; }

    /* =================================================================== */
    /*  Expressions                                                         */
    /* =================================================================== */

    public String visit(Expression n, RenameContext argu) { return emit(n.f0.choice, argu); }

    public String visit(AndExpression n, RenameContext argu) {
        return emit(n.f0, argu) + " & " + emit(n.f2, argu);
    }
    public String visit(CompareExpression n, RenameContext argu) {
        return emit(n.f0, argu) + " < " + emit(n.f2, argu);
    }
    public String visit(PlusExpression n, RenameContext argu) {
        return emit(n.f0, argu) + " + " + emit(n.f2, argu);
    }
    public String visit(MinusExpression n, RenameContext argu) {
        return emit(n.f0, argu) + " - " + emit(n.f2, argu);
    }
    public String visit(TimesExpression n, RenameContext argu) {
        return emit(n.f0, argu) + " * " + emit(n.f2, argu);
    }

    public String visit(ArrayLookup n, RenameContext argu) {
        String base = renameIdent(n.f0.f0.tokenImage, argu);
        return base + "[" + emit(n.f2, argu) + "]";
    }
    public String visit(ArrayLength n, RenameContext argu) {
        String base = renameIdent(n.f0.f0.tokenImage, argu);
        return base + ".length";
    }

    /**
     * MessageSend ::= PrimaryExpression "." Identifier "(" ( ArgList )? ")"
     * Method name (f2) is never renamed.  Receiver (f0) goes through rename.
     */
    public String visit(MessageSend n, RenameContext argu) {
        StringBuilder sb = new StringBuilder();
        sb.append(emit(n.f0, argu)).append(".").append(n.f2.f0.tokenImage).append("(");
        if (n.f4.present()) sb.append(emit(n.f4.node, argu));
        sb.append(")");
        return sb.toString();
    }

    public String visit(ArgList n, RenameContext argu) {
        StringBuilder sb = new StringBuilder();
        sb.append(emit(n.f0, argu));
        for (Node r : n.f1.nodes) sb.append(emit(r, argu));
        return sb.toString();
    }

    public String visit(ArgRest n, RenameContext argu) {
        return ", " + emit(n.f1, argu);
    }

    /* =================================================================== */
    /*  Primary expressions                                                 */
    /* =================================================================== */

    public String visit(PrimaryExpression n, RenameContext argu) {
        return emit(n.f0.choice, argu);
    }

    /**
     * DotExpression ::= Identifier "." Identifier
     * Field name (f2) never renamed.  Base goes through rename.
     */
    public String visit(DotExpression n, RenameContext argu) {
        String baseName = n.f0.f0.tokenImage;
        String base;
        if (argu != null && "this".equals(baseName)) {
            base = "this" + argu.suffix;
        } else {
            base = renameIdent(baseName, argu);
        }
        return base + "." + n.f2.f0.tokenImage;
    }

    public String visit(IntegerLiteral n, RenameContext argu) { return emit(n.f0.choice, argu); }
    public String visit(PlainIntegerLiteral n, RenameContext argu) { return n.f0.tokenImage; }
    public String visit(IntegerLiteralWithPosSign n, RenameContext argu) {
        return "+" + n.f1.tokenImage;
    }
    public String visit(IntegerLiteralWithNegSign n, RenameContext argu) {
        return "-" + n.f1.tokenImage;
    }

    /**
     * ConstOrId ::= IntegerLiteral | Identifier | TrueLiteral | FalseLiteral
     */
    public String visit(ConstOrId n, RenameContext argu) {
        return emit(n.f0.choice, argu);
    }

    public String visit(TrueLiteral n, RenameContext argu)  { return "true"; }
    public String visit(FalseLiteral n, RenameContext argu) { return "false"; }

    /**
     * Identifier — only renamed when reached via a path that doesn't go
     * through one of the field/method specializations above.  Plain
     * AssignmentStatement RHS that turns out to be just an Identifier
     * is one common path here.
     */
    public String visit(Identifier n, RenameContext argu) {
        return renameIdent(n.f0.tokenImage, argu);
    }

    public String visit(ThisExpression n, RenameContext argu) {
        if (argu != null) return "this" + argu.suffix;
        return "this";
    }

    /**
     * ArrayAllocationExpression ::= "new" "int" "[" ConstOrId "]"
     */
    public String visit(ArrayAllocationExpression n, RenameContext argu) {
        return "new int[" + emit(n.f3, argu) + "]";
    }

    /**
     * AllocationExpression ::= "new" Identifier "(" ")"
     */
    public String visit(AllocationExpression n, RenameContext argu) {
        return "new " + n.f1.f0.tokenImage + "()";
    }

    /**
     * NotExpression ::= "!" Identifier
     */
    public String visit(NotExpression n, RenameContext argu) {
        return "!" + renameIdent(n.f1.f0.tokenImage, argu);
    }
}
