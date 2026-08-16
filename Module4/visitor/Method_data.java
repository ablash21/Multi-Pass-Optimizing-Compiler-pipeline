package visitor;
import syntaxtree.*;
import java.util.*;

public class Method_data {
    public String name;
    public String return_type;
    public HashMap<String, var_data> m_vars;     // local variables
    public HashMap<String, var_data> m_args;     // parameters
    public List<String> arg_order;               // param names in declaration order
    public MethodDeclaration methodNode;         // AST node for Pass 5 (inlining)
    public String owner_class;                   // class that defines this method
    public List<IrStmt> ir;                      // Pass 2 fills this; Pass 3 reads it

    public Method_data(String name, String return_type) {
        this.name = name;
        this.return_type = return_type;
        this.m_vars = new HashMap<>();
        this.m_args = new HashMap<>();
        this.arg_order = new ArrayList<>();
        this.methodNode = null;
        this.owner_class = null;
        this.ir = new ArrayList<>();
    }
}
