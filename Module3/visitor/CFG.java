package visitor;
import syntaxtree.*;
import java.util.*;

/**
 * Represents a Control Flow Graph for a single method
 * Uses MEET semantics at merge points (if-else, loops):
 * ⊥ ⊓ x = ⊥ (bottom)
 * c1 ⊓ c1 = c1 (same constant)
 * c1 ⊓ c2 = ⊥ (different constants - lose info)
 * T ⊓ x = x
 */
public class CFG {
    private String methodSignature;
    private CFGNode entry;
    private CFGNode exit;
    private Set<CFGNode> nodes;
    private Map<String, Integer> nodeLabels;  // For unique node labeling
    
    // Map AST Statement to corresponding CFGNode (for optimization in pass5)
    private Map<Statement, CFGNode> stmtToCFGNode = new HashMap<>();
    
    // Parameter names for this method (in order)
    private List<String> parameterNames = new ArrayList<>();
    
    public CFG(String methodSignature) {
        this.methodSignature = methodSignature;
        this.nodes = new LinkedHashSet<>();
        this.nodeLabels = new HashMap<>();
        
        // Create entry and exit nodes
        this.entry = new CFGNode("ENTRY");
        this.exit = new CFGNode("EXIT");
        this.nodes.add(entry);
        this.nodes.add(exit);
    }
    
    public String getMethodSignature() {
        return methodSignature;
    }
    
    public CFGNode getEntry() {
        return entry;
    }
    
    public CFGNode getExit() {
        return exit;
    }
    
    public Set<CFGNode> getNodes() {
        return nodes;
    }
    
    /**
     * Get the statement to CFG node mapping
     */
    public Map<Statement, CFGNode> getStatementToCFGNodeMap() {
        return stmtToCFGNode;
    }
    
    /**
     * Set the statement to CFG node mapping (called by CFGBuilder)
     */
    public void setStatementToCFGNodeMap(Map<Statement, CFGNode> map) {
        this.stmtToCFGNode = map;
    }
    
    /**
     * Create a new basic block node with a unique label
     */
    public CFGNode createNode(String baseName) {
        Integer count = nodeLabels.getOrDefault(baseName, 0);
        count++;
        nodeLabels.put(baseName, count);
        
        CFGNode node = new CFGNode(baseName + "_" + count);
        nodes.add(node);
        return node;
    }
    
    /**
     * Connect two nodes
     */
    public void connect(CFGNode from, CFGNode to) {
        from.addSuccessor(to);
        to.addPredecessor(from);
    }
    
    /**
     * Add a parameter name for this method
     */
    public void addParameterName(String paramName) {
        parameterNames.add(paramName);
    }
    
    /**
     * Get parameter names for this method (in order)
     */
    public List<String> getParameterNames() {
        return parameterNames;
    }
    
    /**
     * Print the CFG structure
     */
    public void printCFG() {
        // Debug output commented out for clean production output
        /*
        System.out.println("\n========================================");
        System.out.println("CFG for: " + methodSignature);
        System.out.println("Total nodes: " + nodes.size());
        System.out.println("========================================");
        
        for (CFGNode node : nodes) {
            System.out.println("\nNode: " + node);
            System.out.println("  Statements: " + node.getStatements().size());
            if (node.getControlFlowNode() != null) {
                System.out.println("  Type: " + node.getNodeType());
            }
            if (node.getReturnVariable() != null) {
                System.out.println("  Returns: " + node.getReturnVariable());
            }
            System.out.print("  Successors: ");
            for (CFGNode succ : node.getSuccessors()) {
                System.out.print(succ.getLabel() + " ");
            }
            System.out.println();
        }
        System.out.println("========================================\n");
        */
    }
}
