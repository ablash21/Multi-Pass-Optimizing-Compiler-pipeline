package visitor;

/**
 * Per-call-site decision produced by Pass 3's post-pass.
 * Pass 4 looks one of these up by AST-node identity:
 *
 *   InlineDecision d = decisions.get(messageSendNode);
 *   if (d != null && d.shouldInline) inline(d.targetClass, d.targetMethod);
 *
 * Built only for /* INLINE *\/-annotated calls.  Non-annotated calls don't
 * appear in the map.
 */
public final class InlineDecision {

    public boolean shouldInline;
    public String targetClass;     // owner class of the resolved method
    public String targetMethod;

    public InlineDecision() {
        this.shouldInline = false;
    }

    @Override
    public String toString() {
        return shouldInline
            ? ("INLINE " + targetClass + "." + targetMethod)
            : "SKIP";
    }
}
