package visitor;
import syntaxtree.*;
import java.util.*;

/**
 * CDA - Conditional Dead Code Analysis
 * 
 * Marks CFG nodes as unreachable based on:
 * 1. Unreachable methods (not in call graph from main)
 * 2. Constant conditions that eliminate branches (if false, while false)
 * 3. Code after unconditional returns
 * 
 * Three-phase approach:
 * Phase 1: Initial marking - all nodes unreachable except main
 * Phase 2: Analysis-time marking - during fixed-point, mark visited nodes
 * Phase 3: Constant branch elimination - if/while with constant conditions
 */
public class CDA {
    
    private ConstantPropagation constantPropagation;
    private Set<String> reachableMethods;
    private Map<String, Set<CFGNode>> reachableNodes;
    
    /**
     * Constructor
     * @param cp - ConstantPropagation with analysis results
     */
    public CDA(ConstantPropagation cp) {
        this.constantPropagation = cp;
        this.reachableMethods = new HashSet<>();
        this.reachableNodes = new HashMap<>();
    }
    
    /**
     * Main entry point - Mark all unreachable code
     * 
     * Algorithm:
     * 1. Mark main method as reachable entry point
     * 2. Propagate reachability through call graph
     * 3. For each reachable method, mark unreachable branches
     */
    public void markAllUnreachable() {
        // System.out.println("\n  CDA: Marking unreachable code...");
        
        // Phase 1: Mark reachable methods via call graph
        markReachableMethods();
        
        // Phase 2: For each reachable method, mark unreachable CFG nodes
        markUnreachableNodes();
    }
    
    /**
     * Phase 1: Mark methods reachable from main via call graph
     * 
     * Uses worklist algorithm:
     * 1. Start with main method as reachable
     * 2. Mark all methods it calls as reachable
     * 3. Recursively mark methods they call
     */
    private void markReachableMethods() {
        Queue<String> methodWorklist = new LinkedList<>();
        
        // Find main method
        String mainMethod = null;
        for (String methodSig : constantPropagation.getAllMethods()) {
            if (methodSig.contains("main")) {
                mainMethod = methodSig;
                break;
            }
        }
        
        if (mainMethod != null) {
            methodWorklist.add(mainMethod);
            reachableMethods.add(mainMethod);
        }
        
        // Propagate reachability through call graph
        while (!methodWorklist.isEmpty()) {
            String method = methodWorklist.poll();
            
            // Get callees of this method
            Set<String> callees = SymbolTable.callGraph.get(method);
            if (callees != null) {
                for (String callee : callees) {
                    if (!reachableMethods.contains(callee)) {
                        reachableMethods.add(callee);
                        methodWorklist.add(callee);
                    }
                }
            }
        }
    }
    
    /**
     * Phase 2: For each reachable method, identify unreachable CFG nodes
     * 
     * A node is unreachable if:
     * 1. The method is not reachable from main, OR
     * 2. The node is in a dead branch (eliminated by constant condition), OR
     * 3. The node is unreachable via control flow
     */
    private void markUnreachableNodes() {
        Map<String, Set<CFGNode>> unreachableByMethod = 
            constantPropagation.unreachableNodes;
        
        for (String methodSig : constantPropagation.getAllMethods()) {
            Set<CFGNode> unreachable = new HashSet<>();
            
            // If method is not reachable from main, mark ALL nodes unreachable
            if (!reachableMethods.contains(methodSig)) {
                CFG cfg = constantPropagation.getMethodCFG(methodSig);
                if (cfg != null) {
                    unreachable.addAll(cfg.getNodes());
                }
            } else {
                // Method is reachable - identify specific unreachable nodes
                CFG cfg = constantPropagation.getMethodCFG(methodSig);
                if (cfg != null) {
                    markDeadBranches(methodSig, cfg, unreachable);
                }
            }
            
            unreachableByMethod.put(methodSig, unreachable);
        }
    }
    
    /**
     * Mark dead branches in a reachable method's CFG
     * 
     * Dead branches occur from:
     * 1. if-statement with constant true condition → else branch dead
     * 2. if-statement with constant false condition → then branch dead
     * 3. while-statement with constant false condition → body dead
     * 
     * @param methodSig - Method signature
     * @param cfg - Method's control flow graph
     * @param unreachable - Set to populate with unreachable nodes
     */
    private void markDeadBranches(String methodSig, CFG cfg, Set<CFGNode> unreachable) {
        // Mark nodes that were identified as unreachable during constant propagation
        for (CFGNode node : cfg.getNodes()) {
            if (node.isUnreachable()) {
                unreachable.add(node);
            }
        }
    }
    
    /**
     * Check if a node is reachable
     * @return true if node is reachable, false if unreachable (dead code)
     */
    public boolean isNodeReachable(String methodSig, CFGNode node) {
        Set<CFGNode> unreachable = constantPropagation.unreachableNodes.get(methodSig);
        if (unreachable == null) {
            return true;
        }
        return !unreachable.contains(node);
    }
    
    /**
     * Get all unreachable nodes for a method
     */
    public Set<CFGNode> getUnreachableNodes(String methodSig) {
        return constantPropagation.unreachableNodes.getOrDefault(methodSig, new HashSet<>());
    }
}
