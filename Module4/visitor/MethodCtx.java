package visitor;

/**
 * Worklist key: a unique analysis instance is identified by (class, method, ctx).
 *
 *   cls    — class that defines the method body (from SymbolTable.resolveMethod)
 *   method — method name
 *   ctx    — caller-side abstract values for this/formals
 */
public final class MethodCtx {

    public final String cls;
    public final String method;
    public final Ctx ctx;

    public MethodCtx(String cls, String method, Ctx ctx) {
        this.cls = cls;
        this.method = method;
        this.ctx = ctx;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodCtx)) return false;
        MethodCtx m = (MethodCtx) o;
        return cls.equals(m.cls) && method.equals(m.method) && ctx.equals(m.ctx);
    }

    @Override
    public int hashCode() {
        return cls.hashCode() * 31 + method.hashCode() * 17 + ctx.hashCode();
    }

    @Override
    public String toString() { return cls + "." + method + "{" + ctx + "}"; }
}
