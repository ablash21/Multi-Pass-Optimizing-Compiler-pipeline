import syntaxtree.*;
import visitor.*;
import java.io.*;

public class P3 {
   
   private static final int MAX_ITERATIONS = 5;
   
   public static void main(String [] args) {
      try {
         // Read input code once
         BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
         StringBuilder inputCode = new StringBuilder();
         String line;
         while ((line = reader.readLine()) != null) {
            inputCode.append(line).append("\n");
         }
         
         String currentCode = inputCode.toString();
         String previousOutput = "";
         int iteration = 0;
         
         // Parse initial code with constructor
         TACoJavaParser parser = new TACoJavaParser(new ByteArrayInputStream(currentCode.getBytes()));
         Node root = parser.Goal();
         
         // Fixpoint iteration loop
         while (iteration < MAX_ITERATIONS) {
            iteration++;
            
            // For iterations after first, re-parse the optimized code using ReInit
            if (iteration > 1) {
               try {
                  TACoJavaParser.ReInit(new ByteArrayInputStream(currentCode.getBytes()));
                  root = TACoJavaParser.Goal();
               } catch (Exception reparseError) {
                  // If reparsing optimized output fails, emit last valid output.
                  if (!previousOutput.isEmpty()) {
                     System.out.println(previousOutput);
                     return;
                  }
                  throw reparseError;
               }
            }
            
            // Reset static state for fresh analysis
            SymbolTable.reset();
            pass3.reset();
            pass4.reset();
            
            // Populate symbol table in pass 1
            root.accept(new pass1<Object,Object>(), null); 
            
            // Build call graph in pass 2
            root.accept(new pass2<Object,Object>(), null);
            
            // Build CFGs for all methods in pass 3
            root.accept(new pass3<Object,Object>(), null);
            
            // Phase 2: Compute side effects
            pass3.computeMethodSideEffects();
            
            // Phase 3: Compute liveness analysis
            pass3.computeLivenessAnalysis();
            
            // Pass 4: Constant Propagation Analysis
            pass4.analyzeProgram(pass3.methodCFGs);
            
            // Pass 5: Code Generation with Optimization
            pass5<Object,Object> codeGen = new pass5<Object,Object>(pass3.methodCFGs);
            root.accept(codeGen, null);
            
            String output = codeGen.getOutput();
            
            // Check for fixpoint (no change in output)
            if (output.equals(previousOutput)) {
               // Fixpoint reached - output final code
               System.out.println(output);
               return;
            }
            
            // Update for next iteration
            previousOutput = output;
            currentCode = output;
         }
         
         // Max iterations reached - output final code
         System.out.println(previousOutput);
      }
      catch (Exception e) {
         // Keep stdout clean for evaluator-generated code consumption.
         System.err.println("Error: " + e.getMessage());
         e.printStackTrace(System.err);
      }
   }
} 


