package visitor;
import syntaxtree.*;
import java.util.*;

/**
 * PredicateEvaluator - Evaluates boolean conditions for control flow optimization
 * 
 * Used in Pass 5 code generation to:
 * - Determine if conditions are constant (true or false)
 * - Eliminate dead code paths (if(true), if(false), while(false))
 * - Guide code generation for conditional statements
 */
public class PredicateEvaluator {
    
    private Pass5ExpressionEvaluator exprEval;
    
    public PredicateEvaluator() {
        this.exprEval = new Pass5ExpressionEvaluator();
    }
    
    /**
     * Evaluate a condition expression to determine branch reachability
     * 
     * @param condition - Expression representing the condition
     * @param constants - Variable constants available at this point
     * @return PredicateEvalResult with evaluation result
     */
    public PredicateEvalResult evaluate(Expression condition, Map<String, LatticeValue> constants) {
        if (condition == null) {
            return new PredicateEvalResult(false, "null");
        }
        
        // Evaluate the expression
        EvalResult result = exprEval.evaluate(condition, constants);
        
        // If result is a boolean constant, extract the value
        if (result.isConstant && result.constantValue instanceof Boolean) {
            boolean value = (Boolean) result.constantValue;
            return new PredicateEvalResult(value, String.valueOf(value));
        }
        
        // Non-constant condition: keep the optimized expression
        if (!result.isConstant) {
            return new PredicateEvalResult(result.optimizedExpr);
        }
        
        // Non-boolean constant (e.g., integer): use as-is
        return new PredicateEvalResult(result.optimizedExpr);
    }
    
    /**
     * Result of predicate evaluation
     */
    public static class PredicateEvalResult {
        /**
         * Is the condition a compile-time constant?
         */
        public boolean isConstantCondition;
        
        /**
         * The constant value if isConstantCondition=true
         */
        public boolean conditionValue;
        
        /**
         * The optimized condition expression for code generation
         */
        public String optimizedCondition;
        
        /**
         * Constructor for constant condition
         */
        public PredicateEvalResult(boolean value, String exprStr) {
            this.isConstantCondition = true;
            this.conditionValue = value;
            this.optimizedCondition = exprStr;
        }
        
        /**
         * Constructor for non-constant condition
         */
        public PredicateEvalResult(String optimizedExpr) {
            this.isConstantCondition = false;
            this.conditionValue = false;  // Unused
            this.optimizedCondition = optimizedExpr;
        }
        
        @Override
        public String toString() {
            if (isConstantCondition) {
                return "PredicateEvalResult(constant=" + conditionValue + ")";
            } else {
                return "PredicateEvalResult(nonConstant, condition='" + optimizedCondition + "')";
            }
        }
    }
}
