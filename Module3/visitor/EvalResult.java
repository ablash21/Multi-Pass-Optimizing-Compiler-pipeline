package visitor;

/**
 * EvalResult - Result of expression evaluation for Pass 5 code generation
 * 
 * Contains both semantic value (is it constant?) and syntactic representation
 * (what string to emit to output code), enabling partial constant folding.
 * 
 * Tracks side effects separately to support splitting statements when needed.
 */
public class EvalResult {
    
    /**
     * Is this expression a compile-time constant?
     */
    public boolean isConstant;
    
    /**
     * The constant value if isConstant=true
     * Type: Integer or Boolean
     */
    public Object constantValue;
    
    /**
     * The optimized expression string for code generation
     * - If isConstant: the constant value as string (e.g., "42", "true")
     * - If !isConstant: expression with substituted constants (e.g., "a + 5", "x < 10")
     */
    public String optimizedExpr;
    
    /**
     * Does evaluating this expression have side effects?
     * 
     * Side effects include:
     *   - PrintStatement in method return
     *   - FieldAssignmentStatement in method
     *   - Object mutations
     * 
     * Used by code generator to decide:
     *   - If hasSideEffects=true and isConstant=true:
     *     SPLIT: "obj.foo(); x = constant;"
     *   - If hasSideEffects=false and isConstant=true:
     *     INLINE: "x = constant;"
     */
    public boolean hasSideEffects;
    
    /**
     * Constructor for constant result
     * @param value - The constant value (Integer or Boolean)
     * @param hasSideEffects - Does computing this have side effects?
     */
    public EvalResult(Object value, boolean hasSideEffects) {
        this.isConstant = true;
        this.constantValue = value;
        this.optimizedExpr = value.toString();
        this.hasSideEffects = hasSideEffects;
    }
    
    /**
     * Constructor for non-constant result
     * @param optimizedExpr - Expression string with constants substituted
     */
    public EvalResult(String optimizedExpr) {
        this.isConstant = false;
        this.constantValue = null;
        this.optimizedExpr = optimizedExpr;
        this.hasSideEffects = false;  // Non-constants don't have verified side effects
    }
    
    /**
     * Constructor for non-constant result with side effects
     * @param optimizedExpr - Expression string
     * @param hasSideEffects - Does this expression have side effects?
     */
    public EvalResult(String optimizedExpr, boolean hasSideEffects) {
        this.isConstant = false;
        this.constantValue = null;
        this.optimizedExpr = optimizedExpr;
        this.hasSideEffects = hasSideEffects;
    }
    
    @Override
    public String toString() {
        if (isConstant) {
            return "EvalResult(constant=" + constantValue + ", sideEffects=" + hasSideEffects + ")";
        } else {
            return "EvalResult(nonConstant, expr='" + optimizedExpr + "', sideEffects=" + hasSideEffects + ")";
        }
    }
}
