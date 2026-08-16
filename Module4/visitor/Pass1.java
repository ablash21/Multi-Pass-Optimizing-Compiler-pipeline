package visitor;
import syntaxtree.*;
import java.util.*;

/**
 * Pass 1 — ClassHierarchyVisitor.
 *
 * Walks the entire AST once and populates {@link SymbolTable} with:
 *   - every class (and its parent if `extends`)
 *   - every field
 *   - every method (with its AST node stashed for Pass 5)
 *   - every formal parameter (in declaration order)
 *   - every local variable
 *
 * Pass 1 does NO points-to analysis and emits NO IR. That is Pass 2's job.
 *
 *  R = String   — bubbles type strings ("int", "boolean", "int[]", "Foo") up
 *                 from Type / Identifier visits.
 *  A = String   — flows context downward:
 *                   "ClassName"               while visiting class-level fields
 *                   "ClassName.methodName"    while visiting method params/locals
 */
public class Pass1 extends GJDepthFirst<String, String> {

    // ---------------------------------------------------------------- Goal
    /**
     * f0 -> MainClass()
     * f1 -> ( TypeDeclaration() )*
     * f2 -> <EOF>
     */
    public String visit(Goal n, String argu) {
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        return null;
    }

    // ----------------------------------------------------------- MainClass
    /**
     * The main class has a synthetic method named "main" whose only formal is
     * String[] argv. We register it so Pass 2 can attach an IR list to it and
     * Pass 3 can seed the worklist at (MainClass, main, emptyCtx).
     *
     * f1  -> Identifier()      class name
     * f11 -> Identifier()      args param name
     * f14 -> ( VarDeclaration() )*
     * f15 -> ( Statement() )*
     */
    public String visit(MainClass n, String argu) {
        String className = n.f1.f0.tokenImage;
        String argsName  = n.f11.f0.tokenImage;

        class_data cd = new class_data(className);
        SymbolTable.class_map.put(className, cd);

        Method_data md = new Method_data("main", "void");
        md.owner_class = className;
        // String[] is not a real type in FunkyTACoJava but record it for completeness.
        md.m_args.put(argsName, new var_data(argsName, "String[]"));
        md.arg_order.add(argsName);
        // No MethodDeclaration AST node for main — it's inline in MainClass.
        cd.m_methods.put("main", md);

        // Now visit locals; A = "MainClass.main" so VarDeclaration registers them
        // as locals of main.
        String ctx = className + ".main";
        n.f14.accept(this, ctx);   // locals
        // f15 statements: nothing for Pass 1 to do (Pass 2 will handle).
        return null;
    }

    // ----------------------------------------------------- TypeDeclaration
    /**
     * f0 -> ClassDeclaration() | ClassExtendsDeclaration()
     */
    public String visit(TypeDeclaration n, String argu) {
        n.f0.accept(this, argu);
        return null;
    }

    // ---------------------------------------------------- ClassDeclaration
    /**
     * f1 -> Identifier()
     * f3 -> ( VarDeclaration() )*   fields
     * f4 -> ( MethodDeclaration() )*
     */
    public String visit(ClassDeclaration n, String argu) {
        String className = n.f1.f0.tokenImage;
        class_data cd = new class_data(className);
        SymbolTable.class_map.put(className, cd);

        // Visit fields with A = className so VarDeclaration installs them as fields.
        n.f3.accept(this, className);
        // Visit methods with A = className so MethodDeclaration knows its owner.
        n.f4.accept(this, className);
        return null;
    }

    // --------------------------------------------- ClassExtendsDeclaration
    /**
     * f1 -> Identifier()       class name
     * f3 -> Identifier()       parent name
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     */
    public String visit(ClassExtendsDeclaration n, String argu) {
        String className  = n.f1.f0.tokenImage;
        String parentName = n.f3.f0.tokenImage;

        class_data cd = new class_data(className);
        cd.parent_name = parentName;
        SymbolTable.class_map.put(className, cd);

        n.f5.accept(this, className);
        n.f6.accept(this, className);
        return null;
    }

    // ----------------------------------------------------- VarDeclaration
    /**
     * f0 -> Type()
     * f1 -> Identifier()
     *
     * Behavior depends on A:
     *   "ClassName"               -> install as field of ClassName
     *   "ClassName.methodName"    -> install as local of that method
     */
    public String visit(VarDeclaration n, String argu) {
        String type = n.f0.accept(this, argu);   // type string from Type visit
        String name = n.f1.f0.tokenImage;

        if (argu == null) return null;           // defensive: shouldn't happen

        if (argu.contains(".")) {
            // method-local
            String[] parts = argu.split("\\.", 2);
            class_data cd = SymbolTable.class_map.get(parts[0]);
            if (cd != null) {
                Method_data md = cd.m_methods.get(parts[1]);
                if (md != null) {
                    md.m_vars.put(name, new var_data(name, type));
                }
            }
        } else {
            // class field
            class_data cd = SymbolTable.class_map.get(argu);
            if (cd != null) {
                cd.m_fields.put(name, new var_data(name, type));
            }
        }
        return null;
    }

    // --------------------------------------------------- MethodDeclaration
    /**
     * f1 -> Type()                 return type
     * f2 -> Identifier()           method name
     * f4 -> ( FormalParameterList() )?
     * f7 -> ( VarDeclaration() )*  locals
     * f8 -> ( Statement() )*
     * f10-> ConstOrId()            return expr (ignored in Pass 1)
     *
     * A entering this visit is "ClassName".
     */
    public String visit(MethodDeclaration n, String argu) {
        String className = argu;
        String retType   = n.f1.accept(this, argu);
        String methName  = n.f2.f0.tokenImage;

        Method_data md = new Method_data(methName, retType);
        md.methodNode = n;
        md.owner_class = className;

        class_data cd = SymbolTable.class_map.get(className);
        // Register the method BEFORE visiting params/locals so nested visits find it.
        cd.m_methods.put(methName, md);

        String methCtx = className + "." + methName;
        n.f4.accept(this, methCtx);   // formals
        n.f7.accept(this, methCtx);   // locals
        // f8 statements & f10 return: Pass 2 handles these
        return null;
    }

    // ------------------------------------------------- FormalParameterList
    /**
     * f0 -> FormalParameter()
     * f1 -> ( FormalParameterRest() )*
     */
    public String visit(FormalParameterList n, String argu) {
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        return null;
    }

    // ----------------------------------------------------- FormalParameter
    /**
     * f0 -> Type()
     * f1 -> Identifier()
     *
     * A is "ClassName.methodName".
     */
    public String visit(FormalParameter n, String argu) {
        String type = n.f0.accept(this, argu);
        String name = n.f1.f0.tokenImage;

        if (argu == null || !argu.contains(".")) return null;
        String[] parts = argu.split("\\.", 2);
        class_data cd = SymbolTable.class_map.get(parts[0]);
        if (cd == null) return null;
        Method_data md = cd.m_methods.get(parts[1]);
        if (md == null) return null;

        md.m_args.put(name, new var_data(name, type));
        md.arg_order.add(name);
        return null;
    }

    // ------------------------------------------------- FormalParameterRest
    /**
     * f0 -> ","
     * f1 -> FormalParameter()
     *
     * Without overriding this, the base visitor would still recurse, but it's
     * cleaner to make the delegation explicit.
     */
    public String visit(FormalParameterRest n, String argu) {
        n.f1.accept(this, argu);
        return null;
    }

    // ----------------------------------------------------------------- Type
    /**
     * Type ::= ArrayType | BooleanType | IntegerType | Identifier
     */
    public String visit(Type n, String argu) {
        return n.f0.accept(this, argu);
    }

    public String visit(ArrayType   n, String argu) { return "int[]";   }
    public String visit(BooleanType n, String argu) { return "boolean"; }
    public String visit(IntegerType n, String argu) { return "int";     }

    // ------------------------------------------------------- Identifier
    /**
     * When Identifier is the choice picked inside Type, return the class name.
     * Other call sites that visit Identifier (e.g. inside a class header) read
     * tokenImage directly and ignore this return value.
     */
    public String visit(Identifier n, String argu) {
        return n.f0.tokenImage;
    }
}
