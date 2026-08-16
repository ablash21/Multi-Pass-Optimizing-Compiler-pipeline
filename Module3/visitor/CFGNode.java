package visitor;
import syntaxtree.*;
import java.util.*;

/**
 * Represents a node (basic block) in the Control Flow Graph
 */
public class CFGNode {
    private static int nodeCounter = 0;
    private int nodeId;
    
    // List of non-control-flow statements in this basic block
    private List<Statement> statements;
    
    // Control flow statement (if this node branches): IfStatement, WhileStatement, ForStatement
    // null for linear flow
    private Node controlFlowNode;
    
    // Type of control flow: "if", "while", "for", "normal"
    private String nodeType;
    
    // Successors: can have 1 (normal/loop) or 2 (if/for branches)
    private List<CFGNode> successors;
    
    // Predecessors for data flow analysis
    private List<CFGNode> predecessors;
    
    // For debugging/visualization
    private String label;
    
    // For return nodes: stores the variable being returned
    private String returnVariable;
    
    // Track if this node is unreachable (dead code)
    private boolean unreachable = false;
    
    // IN/OUT lattice for data flow analysis (maps variable -> LatticeValue)
    public Map<String, LatticeValue> in;
    public Map<String, LatticeValue> out;
    
    public CFGNode(String label) {
        this.nodeId = nodeCounter++;
        this.label = label;
        this.statements = new ArrayList<>();
        this.controlFlowNode = null;
        this.nodeType = "normal";
        this.successors = new ArrayList<>();
        this.predecessors = new ArrayList<>();
        this.in = new HashMap<>();
        this.out = new HashMap<>();
    }
    
    public int getNodeId() {
        return nodeId;
    }
    
    public String getLabel() {
        return label;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }
    
    public List<Statement> getStatements() {
        return statements;
    }
    
    public void addStatement(Statement stmt) {
        statements.add(stmt);
    }
    
    public Node getControlFlowNode() {
        return controlFlowNode;
    }
    
    public void setControlFlowNode(Node node, String type) {
        this.controlFlowNode = node;
        this.nodeType = type;
    }
    
    public void setReturnVariable(String variable) {
        this.returnVariable = variable;
    }
    
    public String getReturnVariable() {
        return returnVariable;
    }
    
    public String getNodeType() {
        return nodeType;
    }
    
    public List<CFGNode> getSuccessors() {
        return successors;
    }
    
    public void addSuccessor(CFGNode succ) {
        if (!successors.contains(succ)) {
            successors.add(succ);
        }
    }
    
    public List<CFGNode> getPredecessors() {
        return predecessors;
    }
    
    public void addPredecessor(CFGNode pred) {
        if (!predecessors.contains(pred)) {
            predecessors.add(pred);
        }
    }
    
    public boolean isUnreachable() {
        return unreachable;
    }
    
    public void setUnreachable(boolean unreachable) {
        this.unreachable = unreachable;
    }
    
    @Override
    public String toString() {
        return "CFGNode(" + nodeId + ":" + label + ") [" + nodeType + "]";
    }
    
    public void printNode() {
        System.out.println("\n" + toString());
        System.out.println("  Statements: " + statements.size());
        System.out.println("  ControlFlow: " + (controlFlowNode != null ? nodeType : "none"));
        if (returnVariable != null) {
            System.out.println("  Returns: " + returnVariable);
        }
        System.out.println("  Successors: " + successors.size());
        System.out.println("  Predecessors: " + predecessors.size());
    }
    
    /**
     * Compute IN lattice for this node by MEET-ing OUT values from all predecessors
     * MEET semantics: ⊥ ⊓ x = ⊥, c1 ⊓ c1 = c1, c1 ⊓ c2 = ⊥, T ⊓ x = x
     * Returns: true if IN changed, false otherwise (for fixed-point iteration)
     */
    public boolean computeInFromPredecessors() {
        Map<String, LatticeValue> newIn = new HashMap<>();
        
        if (predecessors.isEmpty()) {
            // Entry node - IN is all ⊥
            return false;
        }
        
        // Get variables from all predecessors' OUT
        Set<String> allVars = new HashSet<>();
        for (CFGNode pred : predecessors) {
            allVars.addAll(pred.out.keySet());
        }
        
        // MEET values from all predecessors for each variable
        for (String var : allVars) {
            LatticeValue result = null;
            for (CFGNode pred : predecessors) {
                LatticeValue predValue = pred.out.getOrDefault(var, LatticeValue.bottom());
                if (result == null) {
                    result = predValue;
                } else {
                    result = LatticeValue.meet(result, predValue);
                }
            }
            if (result != null) {
                newIn.put(var, result);
            }
        }
        
        // Check if IN changed
        boolean changed = !newIn.equals(this.in);
        this.in = newIn;
        return changed;
    }
}

