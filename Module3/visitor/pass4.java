package visitor;
import syntaxtree.*;
import java.util.*;

/**
 * Pass 4: Inter-Procedural Conditional Constant Propagation
 * 
 * Performs context-insensitive analysis using:
 * - Worklist algorithm for method scheduling
 * - Fixed-point iteration within each method
 * - Dead code detection via constant conditions
 */
public class pass4 {
   
   // Pass 4 analysis orchestrator
   public static ConstantPropagation analysis;
   
   /**
    * ENTRY POINT: Run Pass 4 analysis from main
    * 
    * Called after Pass 3 (CFG generation) completes
    * 
    * @param cfgs - Method CFGs from pass3, keyed by method signature
    */
   public static void analyzeProgram(Map<String, CFG> cfgs) {
      // System.out.println("\n========================================");
      // System.out.println("    PASS 4: CONSTANT PROPAGATION");
      // System.out.println("========================================\n");
      
      // Create orchestrator with CFGs from Pass 3
      analysis = new ConstantPropagation(cfgs);
      
      // Run the main analysis
      analysis.analyzeAllMethods();
      
      // Print results for debugging
      // analysis.printResults();
   }
   
   /**
    * Reset static state for fixpoint iteration
    */
   public static void reset() {
      analysis = null;
   }
}

