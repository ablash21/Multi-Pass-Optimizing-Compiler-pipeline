package visitor;
import syntaxtree.*;
import java.util.*;

/**
 * DeadCodeDetector - Identifies dead code statements
 * 
 * Dead code rules:
 * 1. Unreachable Methods: If method is in SymbolTable.unreachableMethods, skip entire method
 * 2. Unused Definitions: x = expr where x NOT in liveVars AND expr has no side effects
 * 3. Dead Function Calls: a.foo(); where foo() has NO side effects
 * 4. Unreachable Branches: Handled by PredicateEvaluator (if/while with constant conditions)
 */
public class DeadCodeDetector {
    
    /**
     * Check if a statement is dead code and should be skipped
     * 
     * @param stmt - Statement to check
     * @param methodSig - Method signature (ClassName.methodName)
     * @param cfgNode - CFGNode for this statement (used to lookup liveness from SymbolTable)
     * @param exprEvaluator - Expression evaluator to check for side effects
     * @return true if statement is dead code and should be skipped
     */
    public boolean isDead(Statement stmt, String methodSig, CFGNode cfgNode, Pass5ExpressionEvaluator exprEvaluator) {
        
        if (stmt == null || stmt.f0 == null) {
            return false;
        }
        
        // Extract the actual choice from Statement's NodeChoice
        Object choice = stmt.f0.choice;
        
        // Rule 1: Dead function calls - VoidMessageSendStatement with no side effects
        if (choice instanceof VoidMessageSendStatement) {
            return isDeadFunctionCall((VoidMessageSendStatement) choice);
        }
        
        // Rule 2: Unused definitions - x = expr where x not live AND expr has no side effects
        if (choice instanceof AssignmentStatement) {
            return isUnusedDefinition((AssignmentStatement) choice, methodSig, cfgNode, exprEvaluator);
        }
        
        // All other statements are not dead code (blocks, ifs, whiles, etc. handled separately)
        return false;
    }
    
    /**
     * Check if method is unreachable
     * 
     * @param methodSig - Method signature (ClassName.methodName)
     * @return true if method is in unreachable set
     */
    public boolean isUnreachableMethod(String methodSig) {
        return SymbolTable.unreachableMethods.contains(methodSig);
    }
    
    /**
     * Rule 1: Dead Function Call Detection
     * Making public so pass5.java visitor can use it
     * 
     * If stmt is: a.foo();  (VoidMessageSendStatement)
     * AND method foo has NO side effects (check SymbolTable.methodSideEffects[methodSig])
     * THEN statement is dead
     * 
     * @param voidStmt - VoidMessageSendStatement to check
     * @return true if this is a dead function call
     */
    public boolean isDeadFunctionCall(VoidMessageSendStatement voidStmt) {
        try {
            // Extract method name and type from VoidMessageSendStatement
            // VoidMessageSendStatement structure (from grammar):
            //   PrimaryExpression f0
            //   Identifier f1  (method name)
            
            MessageSend msgSend = voidStmt.f0;  // MessageSend inside the statement
            Identifier methodIdent = msgSend.f2;  // Method name (f2 is Identifier)
            String methodName = methodIdent.f0.tokenImage;
            
            // Get primary expression to determine object type
            PrimaryExpression primaryExpr = msgSend.f0;
            Object primaryChoice = primaryExpr.f0.choice;
            
            // Case 1: this.foo() - use current method's class
            if (primaryChoice instanceof ThisExpression) {
                // For this calls, we'd need the current class context
                // For now, return false (keep the call) to be safe
                // In pass5.java, we'll have currClass available
                return false;
            }
            
            // Case 2: obj.foo() - determine type from identifier
            if (primaryChoice instanceof Identifier) {
                Identifier objIdent = (Identifier) primaryChoice;
                String objName = objIdent.f0.tokenImage;
                
                // Try to get type from SymbolTable
                if (SymbolTable.class_map.containsKey(objName)) {
                    String methodSig = objName + "." + methodName;
                    
                    // Check if method has side effects
                    Boolean hasSideEffects = SymbolTable.methodSideEffects.getOrDefault(methodSig, true);
                    
                    if (hasSideEffects == false) {
                        // Method has NO side effects - this call is dead code
                        return true;
                    }
                }
            }
            
        } catch (Exception e) {
            // Any error - conservatively keep the statement
            return false;
        }
        
        return false;
    }
    
    /**
     * Rule 2: Unused Definition Detection
     * 
     * If stmt is: x = expr
     * AND x NOT in liveVars (variable never used after this point)
     * AND expr has NO side effects
     * THEN statement is dead
     * 
     * @param assignment - AssignmentStatement to check
     * @param methodSig - Method signature to lookup in liveness map
     * @param cfgNode - CFGNode for this statement
     * @param exprEvaluator - Expression evaluator to check side effects
     * @return true if this is an unused definition
     */
    public boolean isUnusedDefinition(AssignmentStatement assignment, String methodSig, CFGNode cfgNode, Pass5ExpressionEvaluator exprEvaluator) {
        try {
            // Get variable name being assigned to
            String varName = assignment.f0.f0.tokenImage;
            
            // Look up liveness info from SymbolTable: Map<methodSig, Map<CFGNode, Set<String>>>
            if (SymbolTable.liveness.containsKey(methodSig) && cfgNode != null) {
                Map<Object, Set<String>> methodLiveness = SymbolTable.liveness.get(methodSig);
                Set<String> liveVars = methodLiveness.get(cfgNode);
                
                if (liveVars != null && liveVars.contains(varName)) {
                    // Variable is live (will be used) - not dead code
                    return false;
                }
            }
            
            // Variable is not live (or liveness not computed) - check if expression has side effects
            Expression expr = assignment.f2;
            
            // Check for side effects in expression
            // Side effects include: method calls, array/field access for assignment purposes
            if (hasSideEffects(expr, exprEvaluator)) {
                // Expression has side effects - keep the statement
                return false;
            }
            
            // Variable not live AND expression has no side effects - DEAD CODE
            return true;
            
        } catch (Exception e) {
            // Any error - conservatively keep the statement
            return false;
        }
    }
    
    /**
     * Check if an expression has side effects
     * 
     * Side effects include:
     * - Method calls (MessageSend)
     * - Array/field writes (but those are separate statement types)
     * 
     * @param expr - Expression to check
     * @param exprEvaluator - Expression evaluator with side effect info
     * @return true if expression has observable side effects
     */
    private boolean hasSideEffects(Expression expr, Pass5ExpressionEvaluator exprEvaluator) {
        if (expr == null) {
            return false;
        }
        
        // Extract the actual choice from Expression
        Object choice = expr.f0.choice;
        
        // MessageSend (method call) has potential side effects
        if (choice instanceof MessageSend) {
            MessageSend msgSend = (MessageSend) choice;
            try {
                Identifier methodIdent = msgSend.f2;
                String methodName = methodIdent.f0.tokenImage;
                
                // Conservative: assume method calls have side effects unless explicitly marked
                return true;  // Better to keep than to remove
            } catch (Exception e) {
                return true;  // Default to safe choice
            }
        }
        
        // Primary expressions (literals, identifiers, etc.) don't have side effects
        else if (choice instanceof PrimaryExpression) {
            return false;
        }
        
        // Binary operations don't have side effects
        else if (choice instanceof PlusExpression || choice instanceof MinusExpression ||
                 choice instanceof TimesExpression || choice instanceof CompareExpression) {
            return false;
        }
        
        // Default: assume side effects to be safe
        return true;
    }
}
