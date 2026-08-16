package visitor;
import syntaxtree.*;
import java.util.*;

/**
 * Pass1Visitor — builds SymbolTable.class_map from the AST.
 *
 * Tracks:
 *   curr_class  — name of class being visited
 *   curr_method — name of method being visited (null at class level)
 *
 * After this pass:
 *   SymbolTable.class_map has every class with:
 *     - parent name
 *     - all fields (name + type)
 *     - all methods with:
 *         - return type
 *         - parameters (name + type + order)
 *         - local variables (name + type)
 *         - the MethodDeclaration AST node
 */
public class Pass1Visitor extends pass1<String, Void> {

    // globals — updated as we enter/leave classes and methods
    private String curr_class  = null;
    private String curr_method = null;

    // -------------------------------------------------------
    // Goal — entry point
    // f0 -> MainClass()
    // f1 -> ( TypeDeclaration() )*
    // f2 -> <EOF>
    // -------------------------------------------------------
    @Override
    public String visit(Goal n, Void argu) {
        n.f0.accept(this, argu);   // MainClass
        n.f1.accept(this, argu);   // TypeDeclarations
        return null;
    }

    // -------------------------------------------------------
    // MainClass
    // f0  -> "class"
    // f1  -> Identifier()         class name
    // f11 -> Identifier()         args name (ignore)
    // f14 -> ( VarDeclaration() )*
    // f15 -> ( Statement() )*     (no methods — just main)
    // -------------------------------------------------------
    @Override
    public String visit(MainClass n, Void argu) {
        String className = n.f1.f0.tokenImage;
        curr_class  = className;
        curr_method = null;

        class_data cd = new class_data(className);
        SymbolTable.class_map.put(className, cd);

        // main method — create a synthetic Method_data for it
        // so Pass2 can analyze statements inside main
        Method_data main_md = new Method_data("main", "void");
        cd.m_methods.put("main", main_md);
        curr_method = "main";

        // var declarations inside main
        n.f14.accept(this, argu);

        // store AST node reference — main has no MethodDeclaration node
        // so we leave methodNode null (special case in Pass2)

        curr_method = null;
        curr_class  = null;
        return null;
    }

    // -------------------------------------------------------
    // TypeDeclaration — just dispatch to choice
    // f0 -> ClassDeclaration() | ClassExtendsDeclaration()
    // -------------------------------------------------------
    @Override
    public String visit(TypeDeclaration n, Void argu) {
        n.f0.accept(this, argu);
        return null;
    }

    // -------------------------------------------------------
    // ClassDeclaration
    // f0 -> "class"
    // f1 -> Identifier()      class name
    // f2 -> "{"
    // f3 -> ( VarDeclaration() )*
    // f4 -> ( MethodDeclaration() )*
    // f5 -> "}"
    // -------------------------------------------------------
    @Override
    public String visit(ClassDeclaration n, Void argu) {
        String className = n.f1.f0.tokenImage;
        curr_class  = className;
        curr_method = null;

        class_data cd = new class_data(className);
        // no parent
        SymbolTable.class_map.put(className, cd);

        // collect fields
        n.f3.accept(this, argu);

        // collect methods
        n.f4.accept(this, argu);

        curr_class  = null;
        curr_method = null;
        return null;
    }

    // -------------------------------------------------------
    // ClassExtendsDeclaration
    // f0 -> "class"
    // f1 -> Identifier()      class name
    // f2 -> "extends"
    // f3 -> Identifier()      parent name
    // f4 -> "{"
    // f5 -> ( VarDeclaration() )*
    // f6 -> ( MethodDeclaration() )*
    // f7 -> "}"
    // -------------------------------------------------------
    @Override
    public String visit(ClassExtendsDeclaration n, Void argu) {
        String className  = n.f1.f0.tokenImage;
        String parentName = n.f3.f0.tokenImage;
        curr_class  = className;
        curr_method = null;

        class_data cd = new class_data(className);
        cd.parent_name = parentName;
        SymbolTable.class_map.put(className, cd);

        // collect fields
        n.f5.accept(this, argu);

        // collect methods
        n.f6.accept(this, argu);

        curr_class  = null;
        curr_method = null;
        return null;
    }

    // -------------------------------------------------------
    // VarDeclaration
    // f0 -> Type()
    // f1 -> Identifier()   var name
    // f2 -> ";"
    //
    // Used at BOTH class level (field) and method level (local)
    // -------------------------------------------------------
    @Override
    public String visit(VarDeclaration n, Void argu) {
        String typeName = getTypeName(n.f0);
        String varName  = n.f1.f0.tokenImage;

        var_data vd = new var_data(varName, typeName);

        class_data cd = SymbolTable.class_map.get(curr_class);

        if (curr_method == null) {
            // class-level field
            cd.m_fields.put(varName, vd);
        } else {
            // local variable inside a method
            cd.m_methods.get(curr_method).m_vars.put(varName, vd);
        }

        return null;
    }

    // -------------------------------------------------------
    // MethodDeclaration
    // f0  -> "public"
    // f1  -> Type()           return type
    // f2  -> Identifier()     method name
    // f3  -> "("
    // f4  -> ( FormalParameterList() )?
    // f5  -> ")"
    // f6  -> "{"
    // f7  -> ( VarDeclaration() )*   locals
    // f8  -> ( Statement() )*        body — NOT visited in Pass1
    // f9  -> "return"
    // f10 -> ConstOrId()
    // f11 -> ";"
    // f12 -> "}"
    // -------------------------------------------------------
    @Override
    public String visit(MethodDeclaration n, Void argu) {
        String retType    = getTypeName(n.f1);
        String methodName = n.f2.f0.tokenImage;

        curr_method = methodName;

        Method_data md = new Method_data(methodName, retType);
        md.methodNode = n;   // store AST node — Pass3 needs this for inlining

        class_data cd = SymbolTable.class_map.get(curr_class);
        cd.m_methods.put(methodName, md);

        // collect parameters
        if (n.f4.present()) {
            n.f4.accept(this, argu);
        }

        // collect local variable declarations
        // NOTE: do NOT visit f8 (statements) — Pass1 only builds the table
        n.f7.accept(this, argu);

        curr_method = null;
        return null;
    }

    // -------------------------------------------------------
    // FormalParameterList
    // f0 -> FormalParameter()
    // f1 -> ( FormalParameterRest() )*
    // -------------------------------------------------------
    @Override
    public String visit(FormalParameterList n, Void argu) {
        n.f0.accept(this, argu);   // first param
        n.f1.accept(this, argu);   // rest
        return null;
    }

    // -------------------------------------------------------
    // FormalParameter
    // f0 -> Type()
    // f1 -> Identifier()   param name
    // -------------------------------------------------------
    @Override
    public String visit(FormalParameter n, Void argu) {
        String typeName  = getTypeName(n.f0);
        String paramName = n.f1.f0.tokenImage;

        var_data vd = new var_data(paramName, typeName);

        class_data  cd = SymbolTable.class_map.get(curr_class);
        Method_data md = cd.m_methods.get(curr_method);

        md.m_args.put(paramName, vd);
        md.arg_order.add(paramName);   // preserve order for call matching

        return null;
    }

    // -------------------------------------------------------
    // FormalParameterRest
    // f0 -> ","
    // f1 -> FormalParameter()
    // -------------------------------------------------------
    @Override
    public String visit(FormalParameterRest n, Void argu) {
        n.f1.accept(this, argu);
        return null;
    }

    // -------------------------------------------------------
    // Type — returns type name as String
    // f0 -> ArrayType() | BooleanType() | IntegerType() | Identifier()
    // -------------------------------------------------------
    @Override
    public String visit(Type n, Void argu) {
        return n.f0.accept(this, argu);
    }

    @Override
    public String visit(ArrayType n, Void argu) {
        return "int[]";
    }

    @Override
    public String visit(BooleanType n, Void argu) {
        return "boolean";
    }

    @Override
    public String visit(IntegerType n, Void argu) {
        return "int";
    }

    @Override
    public String visit(Identifier n, Void argu) {
        return n.f0.tokenImage;
    }

    // -------------------------------------------------------
    // Helper — extract type name string from a Type node
    // -------------------------------------------------------
    private String getTypeName(Type t) {
        Node choice = t.f0.choice;
        if (choice instanceof ArrayType)   return "int[]";
        if (choice instanceof BooleanType) return "boolean";
        if (choice instanceof IntegerType) return "int";
        if (choice instanceof Identifier)
            return ((Identifier) choice).f0.tokenImage;
        return "unknown";
    }
}