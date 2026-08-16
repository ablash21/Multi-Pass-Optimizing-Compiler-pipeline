
package visitor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SymbolTable {
    public static HashMap<String, class_data> class_map = new HashMap<>();
    
    // Class Hierarchy: className -> parent class name
    public static HashMap<String, String> classHierarchy = new HashMap<>();
    
    // Class Children: parent class name -> set of child classes
    public static HashMap<String, Set<String>> classChildren = new HashMap<>();
    
    // Class Methods: className -> set of method names defined in that class
    public static HashMap<String, Set<String>> classMethodsMap = new HashMap<>();
    
    // Call Graph: methodSignature (ClassName.methodName) -> set of called method signatures
    public static HashMap<String, Set<String>> callGraph = new HashMap<>();
    
    // Phase 1: Reachability Analysis
    public static Set<String> reachableMethods = new HashSet<>();
    public static Set<String> unreachableMethods = new HashSet<>();
    
    // Phase 2: Side Effects Analysis
    public static Map<String, Boolean> methodSideEffects = new HashMap<>();
    
    // Phase 3: Liveness Analysis - Map<methodSig, Map<CFGNode, Set<liveVariables>>>
    public static Map<String, Map<Object, Set<String>>> liveness = new HashMap<>();

    public static void printAll() {
        for (Map.Entry<String, class_data> ce : class_map.entrySet()) {
            class_data cd = ce.getValue();
            System.out.println("CLASS: " + cd.name
                + (cd.parent_name != null ? " extends " + cd.parent_name : ""));

            for (Map.Entry<String, var_data> fe : cd.m_fields.entrySet()) {
                var_data vd = fe.getValue();
                System.out.println("  FIELD: " + vd.data_type + " " + vd.name);
            }

            for (Map.Entry<String, Method_data> me : cd.m_methods.entrySet()) {
                Method_data md = me.getValue();
                System.out.println("  METHOD: " + md.return_type + " " + md.name);
                for (Map.Entry<String, var_data> ae : md.m_args.entrySet()) {
                    var_data vd = ae.getValue();
                    System.out.println("    ARG: " + vd.data_type + " " + vd.name);
                }
                for (Map.Entry<String, var_data> ve : md.m_vars.entrySet()) {
                    var_data vd = ve.getValue();
                    System.out.println("    VAR: " + vd.data_type + " " + vd.name);
                }
            }
        }
    }
    
    /**
     * Print class hierarchy information
     */
    public static void printClassHierarchy() {
        System.out.println("\n=== CLASS HIERARCHY ===");
        for (Map.Entry<String, String> entry : classHierarchy.entrySet()) {
            if (entry.getValue() != null) {
                System.out.println(entry.getKey() + " extends " + entry.getValue());
            } else {
                System.out.println(entry.getKey() + " (no parent)");
            }
        }
    }
    
    /**
     * Print call graph information
     */
    public static void printCallGraph() {
        System.out.println("\n=== CALL GRAPH ===");
        for (Map.Entry<String, Set<String>> entry : callGraph.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                System.out.println(entry.getKey() + " calls:");
                for (String callee : entry.getValue()) {
                    System.out.println("  -> " + callee);
                }
            }
        }
    }
    
    // Reset all static fields for next optimization iteration
    public static void reset() {
        class_map.clear();
        classHierarchy.clear();
        callGraph.clear();
        methodSideEffects.clear();
    }
}