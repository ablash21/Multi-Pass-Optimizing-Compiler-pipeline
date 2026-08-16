package visitor;
import java.util.*;

/**
 * MethodSummary - Data holder for inter-procedural information
 * 
 * Stores analysis results for a method:
 * - Return value (what constant does this method return?)
 * - Parameter values (what constants are bound to parameters?)
 * - Modified fields (which class fields are side-effected?)
 */
public class MethodSummary {
    
    // Method identification
    public String methodSig;
    
    // Return value for this method
    // In context-insensitive analysis, this is THE return value (all paths MEETed)
    public LatticeValue returnValue;
    
    // Parameter constant values
    // Maps parameter name -> its constant value at entry
    // Used in inter-procedural propagation
    public Map<String, LatticeValue> parameterValues;
    
    /**
     * Constructor - Initialize a new method summary
     * @param methodSig - method signature (fully qualified name)
     */
    public MethodSummary(String methodSig) {
        this.methodSig = methodSig;
        this.returnValue = LatticeValue.top();  // Start as unknown
        this.parameterValues = new HashMap<>();
    }
    
    /**
     * Set the return value for this method
     * @param value - the LatticeValue to set
     */
    public void setReturnValue(LatticeValue value) {
        this.returnValue = value;
    }
    
    /**
     * Get the return value for this method
     * @return the current return value
     */
    public LatticeValue getReturnValue() {
        return returnValue;
    }
    
    /**
     * Set a parameter's constant value
     * @param paramName - parameter name
     * @param value - the LatticeValue to bind
     */
    public void setParameterValue(String paramName, LatticeValue value) {
        parameterValues.put(paramName, value);
    }
    
    /**
     * Get a parameter's constant value
     * @param paramName - parameter name
     * @return the LatticeValue, or TOP if not found
     */
    public LatticeValue getParameterValue(String paramName) {
        return parameterValues.getOrDefault(paramName, LatticeValue.top());
    }
    
    /**
     * Check if a parameter has a constant value
     * @param paramName - parameter name
     * @return true if parameter value is known/constant
     */
    public boolean hasParameterValue(String paramName) {
        return parameterValues.containsKey(paramName);
    }
    

    
    /**
     * Get all parameter values as a map
     * @return Map of parameter name -> LatticeValue
     */
    public Map<String, LatticeValue> getParameterValues() {
        return new HashMap<>(parameterValues);
    }
    
    /**
     * Get method signature
     * @return the method signature
     */
    public String getMethodSig() {
        return methodSig;
    }
    
    /**
     * Create a copy of this summary (for snapshots)
     * @return a deep copy of this MethodSummary
     */
    public MethodSummary copy() {
        MethodSummary copy = new MethodSummary(this.methodSig);
        copy.returnValue = this.returnValue;
        copy.parameterValues = new HashMap<>(this.parameterValues);
        return copy;
    }
    
    /**
     * Check if this summary has changed from another snapshot
     * @param old - the previous summary to compare against
     * @return true if return value or parameters changed
     */
    public boolean hasChanged(MethodSummary old) {
        if (!returnValue.equals(old.returnValue)) {
            return true;
        }
        
        for (String param : parameterValues.keySet()) {
            LatticeValue oldValue = old.parameterValues.get(param);
            if (!parameterValues.get(param).equals(oldValue)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get string representation of this summary
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("MethodSummary(").append(methodSig).append(")\n");
        sb.append("  Return: ").append(returnValue).append("\n");
        if (!parameterValues.isEmpty()) {
            sb.append("  Parameters:\n");
            for (String param : parameterValues.keySet()) {
                sb.append("    ").append(param).append(" = ")
                    .append(parameterValues.get(param)).append("\n");
            }
        }
        return sb.toString();
    }
}
