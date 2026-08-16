package visitor;
import java.util.*;

/**
 * Renaming context passed to Pass4 when emitting an inlined callee body.
 *
 *   null              -> caller scope; identifiers emit as-is.
 *   non-null instance -> callee scope; locals/formals get suffixed, bare field
 *                        refs rewrite to "this@N.field", and "this" becomes
 *                        "this@N".
 *
 * Built once per inlined call site.
 */
public final class RenameContext {

    public final Method_data target;
    public final String suffix;          // e.g. "@1"
    public final Set<String> localScope; // formals + locals of target
    public final Set<String> fieldScope; // all fields of the receiver's declared class

    public RenameContext(Method_data target, String suffix) {
        this(target, suffix, target.owner_class);
    }

    public RenameContext(Method_data target, String suffix, String fieldOwnerClass) {
        this.target = target;
        this.suffix = suffix;
        this.localScope = new HashSet<>();
        this.localScope.addAll(target.m_args.keySet());
        this.localScope.addAll(target.m_vars.keySet());
        this.fieldScope = new HashSet<>(
            SymbolTable.getAllFields(fieldOwnerClass).keySet());
    }
}
