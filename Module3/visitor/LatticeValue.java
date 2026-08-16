package visitor;
import java.util.*;

/**
 * Represents a lattice value for constant propagation
 * ⊥ (Bottom): Entry point/no information - represented as null
 * Constants: Concrete int/boolean values
 * T (Top): Unknown (multiple values converged)
 */
public class LatticeValue {
    public enum Type {
        BOTTOM,      // ⊥ - no information
        INT,         // constant integer
        BOOLEAN,     // constant boolean
        TOP          // T - unknown (conflict)
    }
    
    private Type type;
    private Integer intValue;    // if type == INT
    private Boolean boolValue;   // if type == BOOLEAN
    
    // Private constructor
    private LatticeValue(Type t, Integer iv, Boolean bv) {
        this.type = t;
        this.intValue = iv;
        this.boolValue = bv;
    }
    
    // Factory methods
    public static LatticeValue bottom() {
        return new LatticeValue(Type.BOTTOM, null, null);
    }
    
    public static LatticeValue intValue(int i) {
        return new LatticeValue(Type.INT, i, null);
    }
    
    public static LatticeValue boolValue(boolean b) {
        return new LatticeValue(Type.BOOLEAN, null, b);
    }
    
    public static LatticeValue top() {
        return new LatticeValue(Type.TOP, null, null);
    }
    
    public Type getType() {
        return type;
    }
    
    public Integer getIntValue() {
        return intValue;
    }
    
    public Boolean getBoolValue() {
        return boolValue;
    }
    
    public boolean isBottom() {
        return type == Type.BOTTOM;
    }
    
    public boolean isTop() {
        return type == Type.TOP;
    }
    
    public boolean isConstant() {
        return type == Type.INT || type == Type.BOOLEAN;
    }
    
    /**
     * Meet operation (for merge points)
     * ⊥ ⊓ x = ⊥ (bottom)
     * c1 ⊓ c1 = c1  (same constant)
     * c1 ⊓ c2 = ⊥   (different constants - lose info)
     * T ⊓ x = x
     */
    public static LatticeValue meet(LatticeValue v1, LatticeValue v2) {
        if (v1 == null || v1.isBottom()) return LatticeValue.bottom();
        if (v2 == null || v2.isBottom()) return LatticeValue.bottom();
        if (v1.isTop()) return v2;
        if (v2.isTop()) return v1;
        
        // Both are constants
        if (v1.type == Type.INT && v2.type == Type.INT) {
            if (v1.intValue.equals(v2.intValue)) {
                return v1;  // Same value - preserve
            } else {
                return LatticeValue.bottom();  // Different values - lose info
            }
        }
        if (v1.type == Type.BOOLEAN && v2.type == Type.BOOLEAN) {
            if (v1.boolValue.equals(v2.boolValue)) {
                return v1;  // Same value - preserve
            } else {
                return LatticeValue.bottom();  // Different values - lose info
            }
        }
        
        // Different types - lose info
        return LatticeValue.bottom();
    }
    
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LatticeValue)) return false;
        LatticeValue other = (LatticeValue) o;
        
        if (this.type != other.type) return false;
        if (type == Type.INT) return this.intValue.equals(other.intValue);
        if (type == Type.BOOLEAN) return this.boolValue.equals(other.boolValue);
        return true;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(type, intValue, boolValue);
    }
    
    @Override
    public String toString() {
        switch (type) {
            case BOTTOM: return "⊥";
            case INT: return String.valueOf(intValue);
            case BOOLEAN: return String.valueOf(boolValue);
            case TOP: return "T";
            default: return "?";
        }
    }
}
