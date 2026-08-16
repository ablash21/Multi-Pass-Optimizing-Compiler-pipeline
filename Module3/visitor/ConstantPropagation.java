package visitor;
import syntaxtree.*;
import java.util.*;

/**
 * ConstantPropagation - Main Orchestrator for Pass 4
 * 
 * Performs inter-procedural conditional constant propagation using:
 * - Worklist algorithm for method scheduling
 * - Fixed-point iteration within each method
 * - Context-insensitive analysis (all calls treated the same)
 */
public class ConstantPropagation {
    
    // Main data structures
    public Map<String, MethodSummary> methodSummaries;  // Global variable - stores all method results
    public Map<String, Set<CFGNode>> unreachableNodes;
    public Map<String, CFG> methodCFGs;  // Global variable - stores all CFGs for methods
    
    // Working data
    private Queue<String> worklist;
    private MethodAnalyzer methodAnalyzer;
    private ExpressionEvaluator exprEvaluator;
    
    /**
     * Constructor
     * @param methodCFGs - CFGs from Pass 3 (methodSig -> CFG)
     */
    public ConstantPropagation(Map<String, CFG> methodCFGs) {
        this.methodCFGs = methodCFGs;
        this.unreachableNodes = new HashMap<>();
        this.methodSummaries = new HashMap<>();
        this.worklist = new LinkedList<>();
        
        // Initialize helper classes
        this.methodAnalyzer = new MethodAnalyzer(this);
        this.exprEvaluator = new ExpressionEvaluator(this);
    }
    
    /**
     * Main entry point - Performs complete inter-procedural analysis
     * 
     * Algorithm:
     * 1. Initialize all method summaries
     * 2. Populate worklist with all methods
     * 3. Iteratively analyze methods until convergence
     * 4. Mark unreachable code via CDA
     */
    public void analyzeAllMethods() {
        // System.out.println("\n=== PASS 4: CONSTANT PROPAGATION ANALYSIS ===\n");
        
        // Step 1: Initialize all method summaries
        initializeMethodSummaries();
        
        // Step 2: Run worklist algorithm until convergence
        runWorklistAlgorithm();
        
        // Step 3: Mark unreachable code
        markUnreachableCode();
        
        // System.out.println("\n=== PASS 4 COMPLETE ===\n");
    }
    
    /**
     * Step 1: Initialize method summaries with default values
     * 
     * - All parameters = T (Top/unknown)
     * - Main method parameters = ⊥ (Bottom/precise)
     * - All return values = T (Top/unknown)
     * - Initialize empty final states for each method
     */
    private void initializeMethodSummaries() {
        // System.out.println("Step 1: Initializing method summaries...");
        
        for (String methodSig : methodCFGs.keySet()) {
            MethodSummary summary = new MethodSummary(methodSig);
            
            // Initialize all parameters as T (unknown)
            CFG cfg = methodCFGs.get(methodSig);
            if (cfg != null) {
                List<String> paramNames = cfg.getParameterNames();
                for (String paramName : paramNames) {
                    summary.setParameterValue(paramName, LatticeValue.top());
                }
            }
            
            // Initialize return value as T (unknown)
            summary.returnValue = LatticeValue.top();
            
            methodSummaries.put(methodSig, summary);
            
            // Initialize empty unreachable nodes
            unreachableNodes.put(methodSig, new HashSet<>());
        }
        
        // System.out.println("  Initialized " + methodSummaries.size() + " method summaries");
    }
    
    /**
     * Step 2: Run worklist algorithm until convergence
     * 
     * For each method M in worklist:
     *   1. Analyze M with current parameter values
     *   2. Check if return value improved (became more precise)
     *   3. If improved, add all callers to worklist
     *   4. Continue until worklist empty (convergence)
     */
    private void runWorklistAlgorithm() {
        // System.out.println("\nStep 2: Running worklist algorithm...");
        
        // Initialize worklist with only main method (entry point)
        // Discovery of other methods happens through inter-procedural analysis
        for (String methodSig : methodCFGs.keySet()) {
            if (methodSig.contains("main")) {
                worklist.add(methodSig);
                break;  // Only one main method
            }
        }
        int iterations = 0;
        
        // System.out.println("\n--- WORKLIST INITIALIZATION ---");
        // for (String methodSig : worklist) {
        //     MethodSummary summary = methodSummaries.get(methodSig);
        //     System.out.println("  Added to worklist: " + methodSig);
        //     System.out.println("    Arguments: " + summary.parameterValues);
        //     System.out.println("    Return value: " + summary.returnValue);
        // }
        // System.out.println();
        
        while (!worklist.isEmpty()) {
            iterations++;
            String methodSig = worklist.poll();
            
            // Get old return value before analysis
            MethodSummary summary = methodSummaries.get(methodSig);
            LatticeValue oldReturn = summary.returnValue;
            
            // System.out.println("\n--- ITERATION " + iterations + " ---");
            // System.out.println("Processing: " + methodSig);
            // System.out.println("  Current return value: " + oldReturn);
            // System.out.println("  Current arguments: " + summary.parameterValues);
            
            // Analyze this method
            LatticeValue newReturn = methodAnalyzer.analyzeMethod(methodSig);
            

            
            // MEET the old and new return values
            LatticeValue improvedReturn = LatticeValue.meet(oldReturn, newReturn);
            
            // Check if return value improved (became more precise)
            if (!improvedReturn.equals(oldReturn)) {
                // System.out.println("  ✓ Return value improved: " + oldReturn + " -> " + improvedReturn);
                
                summary.returnValue = improvedReturn;
                
                // Add all callers to worklist
                Set<String> callers = getCallers(methodSig);
                // System.out.println("  Callers found: " + callers.size());
                for (String caller : callers) {
                    if (!worklist.contains(caller)) {
                        // System.out.println("    → Adding to worklist: " + caller);
                        MethodSummary callerSummary = methodSummaries.get(caller);
                        // System.out.println("      Arguments: " + callerSummary.parameterValues);
                        // System.out.println("      Current return: " + callerSummary.returnValue);
                        worklist.add(caller);
                    }
                }
            } else {
                // System.out.println("  (no improvement)");
            }
        }
        
        // System.out.println("\n  Convergence reached after " + iterations + " iterations");
    }
    
    /**
     * Step 3: Mark unreachable code via CDA
     * 
     * Uses CDA class to:
     * 1. Mark nodes unreachable based on constant conditions
     * 2. Propagate reachability through call graph
     * 3. Identify dead code statements
     */
    private void markUnreachableCode() {
        // System.out.println("\nStep 3: Marking unreachable code...");
        
        CDA cda = new CDA(this);
        cda.markAllUnreachable();
        
        // Count total unreachable nodes
        int totalUnreachable = 0;
        for (Set<CFGNode> nodes : unreachableNodes.values()) {
            totalUnreachable += nodes.size();
        }
        // System.out.println("  Marked " + totalUnreachable + " unreachable nodes");
    }
    
    /**
     * Get all callers of a given method from the call graph
     * @param methodSig - method to find callers for
     * @return Set of methods that call this method
     */
    private Set<String> getCallers(String methodSig) {
        Set<String> callers = new HashSet<>();
        
        // Iterate through call graph to find callers
        for (String method : SymbolTable.callGraph.keySet()) {
            Set<String> callees = SymbolTable.callGraph.get(method);
            if (callees != null && callees.contains(methodSig)) {
                callers.add(method);
            }
        }
        
        return callers;
    }
    

    /**
     * Check if a CFG node is reachable
     * @param methodSig - method containing the node
     * @param node - CFG node to check
     * @return true if node is reachable, false if unreachable (dead code)
     */
    public boolean isNodeReachable(String methodSig, CFGNode node) {
        Set<CFGNode> unreachable = unreachableNodes.get(methodSig);
        if (unreachable == null) {
            return true;
        }
        return !unreachable.contains(node);
    }
    
    /**
     * Get the expression evaluator for evaluating expressions
     */
    public ExpressionEvaluator getExpressionEvaluator() {
        return exprEvaluator;
    }
    
    /**
     * Get the method analyzer for analyzing individual methods
     */
    public MethodAnalyzer getMethodAnalyzer() {
        return methodAnalyzer;
    }
    
    /**
     * Add a method to the worklist (called by inter-procedural analysis)
     */
    public void addMethodToWorklist(String methodSig) {
        if (!worklist.contains(methodSig)) {
            worklist.add(methodSig);
        }
    }
    
    /**
     * Get CFG for a method
     */
    public CFG getMethodCFG(String methodSig) {
        return methodCFGs.get(methodSig);
    }
    
    /**
     * Get all method signatures being analyzed
     */
    public Set<String> getAllMethods() {
        return methodCFGs.keySet();
    }
    
    /**
     * Print analysis results for debugging
     */
    public void printResults() {
        // Debug output commented out for clean production output
        /*
        System.out.println("\n=== CONSTANT PROPAGATION RESULTS ===\n");
        
        for (String methodSig : methodSummaries.keySet()) {
            MethodSummary summary = methodSummaries.get(methodSig);
            System.out.println("Method: " + methodSig);
            
            // Print parameters
            if (!summary.parameterValues.isEmpty()) {
                for (String param : summary.parameterValues.keySet()) {
                    System.out.println("  " + param + " = " + summary.parameterValues.get(param));
                }
            }
            System.out.println("  Return: " + summary.returnValue);
        }
        
        System.out.println("\n=== UNREACHABLE CODE ===\n");
        for (String methodSig : unreachableNodes.keySet()) {
            Set<CFGNode> nodes = unreachableNodes.get(methodSig);
            if (!nodes.isEmpty()) {
                System.out.println("Method: " + methodSig);
                for (CFGNode node : nodes) {
                    System.out.println("  Unreachable node: " + node.getLabel());
                }
            }
        }
        */
    }
}
