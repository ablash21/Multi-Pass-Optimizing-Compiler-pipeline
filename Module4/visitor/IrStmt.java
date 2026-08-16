package visitor;
import java.util.*;

/**
 * IR statement hierarchy.
 * Pass 2 (IRBuilder) produces these from the AST; Pass 3 (PointsToEngine) consumes them.
 * All fields public to keep transfer-function code in Pass 3 short.
 */
public abstract class IrStmt {
}

/* x = new Foo()   ;  siteLabel = "Foo@<lineNum>" */
class AllocStmt extends IrStmt {
    public String lhs;
    public String className;
    public String siteLabel;
    public AllocStmt(String lhs, String className, String siteLabel) {
        this.lhs = lhs; this.className = className; this.siteLabel = siteLabel;
    }
    public String toString() { return lhs + " = new " + className + "()   // " + siteLabel; }
}

/* x = y         (also: x = this) */
class CopyStmt extends IrStmt {
    public String lhs, rhs;
    public CopyStmt(String lhs, String rhs) { this.lhs = lhs; this.rhs = rhs; }
    public String toString() { return lhs + " = " + rhs; }
}

/* x = y.f */
class LoadStmt extends IrStmt {
    public String lhs, base, field;
    public LoadStmt(String lhs, String base, String field) {
        this.lhs = lhs; this.base = base; this.field = field;
    }
    public String toString() { return lhs + " = " + base + "." + field; }
}

/* x.f = y */
class StoreStmt extends IrStmt {
    public String base, field, rhs;
    public StoreStmt(String base, String field, String rhs) {
        this.base = base; this.field = field; this.rhs = rhs;
    }
    public String toString() { return base + "." + field + " = " + rhs; }
}

/* (lhs)? = receiver.method(args)   ; annotated = INLINE present */
class CallStmt extends IrStmt {
    public String lhs;            // null if void-result call
    public String receiver;       // identifier or "this"
    public String method;
    public List<String> args;
    public boolean annotated;
    public int siteId;            // unique per textual call site (for Pass 5 lookup)
    public syntaxtree.MessageSendStatement astNode;  // back-pointer for Pass 5

    public CallStmt(String lhs, String receiver, String method,
                    List<String> args, boolean annotated, int siteId,
                    syntaxtree.MessageSendStatement astNode) {
        this.lhs = lhs; this.receiver = receiver; this.method = method;
        this.args = args; this.annotated = annotated;
        this.siteId = siteId; this.astNode = astNode;
    }
    public String toString() {
        return (lhs != null ? lhs + " = " : "")
             + receiver + "." + method + "(" + String.join(",", args) + ")"
             + (annotated ? "  /*INLINE*/" : "")
             + "  #" + siteId;
    }
}

/* return v   (single trailing return per method in this grammar) */
class ReturnStmt extends IrStmt {
    public String var;
    public ReturnStmt(String var) { this.var = var; }
    public String toString() { return "return " + var; }
}

/* if (...) then-branch else else-branch */
class IfStmt extends IrStmt {
    public List<IrStmt> thenB, elseB;
    public IfStmt(List<IrStmt> thenB, List<IrStmt> elseB) {
        this.thenB = thenB; this.elseB = elseB;
    }
    public String toString() { return "if {...} else {...}"; }
}

/* while (...) body   ; for-loops also lower to this */
class WhileStmt extends IrStmt {
    public List<IrStmt> body;
    public WhileStmt(List<IrStmt> body) { this.body = body; }
    public String toString() { return "while {...}"; }
}

/* No-op: arithmetic, array ops, println, etc. Kept for IR completeness. */
class NopStmt extends IrStmt {
    public String hint;       // optional debug hint
    public NopStmt() { this.hint = ""; }
    public NopStmt(String hint) { this.hint = hint; }
    public String toString() { return "nop" + (hint.isEmpty() ? "" : "(" + hint + ")"); }
}
