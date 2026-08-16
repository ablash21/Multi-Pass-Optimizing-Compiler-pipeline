package visitor;
import syntaxtree.*;
import java.util.*;

/**
 * ExpressionEvaluator - Evaluates expressions at compile time
 * 
 * Handles all 9 expression types with priority: ⊥ > constants > T
 * 
 * PREDICTABLE (can be derived from constants):
 *   - IntegerLiteral, TrueLiteral, FalseLiteral
 *   - Identifier (lookup from state)
 *   - PlusExpression, MinusExpression, TimesExpression (if operands constant)
 *   - CompareExpression (if operands constant)
 *   - NotExpression (if operand constant)
 * 
 * UNPREDICTABLE (always return ⊥):
 *   - ArrayLookup (runtime index unknown)
 *   - ArrayLength (size unknown at compile-time)
 *   - FieldRead (object and field unknown)
 *   - AllocationExpression (reference, not constant)
 *   - ArrayAllocationExpression (array reference, not constant)
 *   - MessageSend (depends on MethodSummary)
 */
public class ExpressionEvaluator {
    
    private ConstantPropagation constantPropagation;
    
    /**
     * Constructor
     * @param cp - reference to ConstantPropagation for method summaries
     */
    public ExpressionEvaluator(ConstantPropagation cp) {
        this.constantPropagation = cp;
    }
    
    /**
     * Evaluate expression according to grammar and lattice rules
     * 
     * @param expr - Expression to evaluate
     * @param state - Current lattice state (variable -> LatticeValue)
     * @return LatticeValue representing the compile-time constant (or ⊥ if unknown)
     */
    public LatticeValue evaluate(Expression expr, Map<String, LatticeValue> state) {
        if (expr == null) {
            return LatticeValue.top();
        }
        
        // Expression is a choice node, extract the actual choice
        Object choice = expr.f0.choice;
        
        // LITERAL EXPRESSIONS (always produce constants)
        if (choice instanceof IntegerLiteral) {
            IntegerLiteral lit = (IntegerLiteral) choice;
            int value = extractIntegerValue(lit);
            return LatticeValue.intValue(value);
        }
        else if (choice instanceof TrueLiteral) {
            return LatticeValue.boolValue(true);
        }
        else if (choice instanceof FalseLiteral) {
            return LatticeValue.boolValue(false);
        }
        
        // IDENTIFIER (lookup from current state)
        else if (choice instanceof Identifier) {
            String name = ((Identifier) choice).f0.tokenImage;
            return state.getOrDefault(name, LatticeValue.top());
        }
        
        // UNPREDICTABLE EXPRESSIONS (always return ⊥/BOTTOM)
        
        else if (choice instanceof ArrayLookup) {
            // a[i] - Don't know index or array contents
            return LatticeValue.bottom();
        }
        else if (choice instanceof ArrayLength) {
            // a.length - Size unknown at compile-time
            return LatticeValue.bottom();
        }
        else if (choice instanceof FieldRead) {
            // obj.field - Object identity and field value unknown
            return LatticeValue.bottom();
        }
        else if (choice instanceof AllocationExpression) {
            // new Class() - Creates a reference (not a constant value)
            return LatticeValue.bottom();
        }
        else if (choice instanceof ArrayAllocationExpression) {
            // new int[n] - Array reference (not a constant value)
            return LatticeValue.bottom();
        }
        else if (choice instanceof MessageSend) {
            // obj.method(...) - Look up return value from method summary
            return evaluateMessageSend((MessageSend) choice, state);
        }
        
        // PRIMARY EXPRESSION: Contains IntegerLiteral, TrueLiteral, FalseLiteral, Identifier, 
        //                     ThisExpression, ArrayAllocationExpression, AllocationExpression, NotExpression
        else if (choice instanceof PrimaryExpression) {
            PrimaryExpression primary = (PrimaryExpression) choice;
            Object primaryChoice = primary.f0.choice;
            
            // Handle each primary type
            if (primaryChoice instanceof IntegerLiteral) {
                IntegerLiteral lit = (IntegerLiteral) primaryChoice;
                int value = extractIntegerValue(lit);
                return LatticeValue.intValue(value);
            }
            else if (primaryChoice instanceof TrueLiteral) {
                return LatticeValue.boolValue(true);
            }
            else if (primaryChoice instanceof FalseLiteral) {
                return LatticeValue.boolValue(false);
            }
            else if (primaryChoice instanceof Identifier) {
                String name = ((Identifier) primaryChoice).f0.tokenImage;
                return state.getOrDefault(name, LatticeValue.top());
            }
            else if (primaryChoice instanceof ThisExpression) {
                // "this" is a reference, not a constant
                return LatticeValue.bottom();
            }
            else if (primaryChoice instanceof ArrayAllocationExpression) {
                // new int[n] - Array reference
                return LatticeValue.bottom();
            }
            else if (primaryChoice instanceof AllocationExpression) {
                // new Class() - Object reference
                return LatticeValue.bottom();
            }
            else if (primaryChoice instanceof NotExpression) {
                // !x - Evaluate the identifier's negation
                NotExpression not = (NotExpression) primaryChoice;
                String name = not.f1.f0.tokenImage;
                LatticeValue operand = state.getOrDefault(name, LatticeValue.top());
                return unaryOperation("!", operand);
            }
        }
        
        // BINARY OPERATIONS
        else if (choice instanceof PlusExpression) {
            PlusExpression plus = (PlusExpression) choice;
            LatticeValue left = evaluateConstOrId(plus.f0, state);
            LatticeValue right = evaluateConstOrId(plus.f2, state);
            return binaryOperation(left, "+", right);
        }
        else if (choice instanceof MinusExpression) {
            MinusExpression minus = (MinusExpression) choice;
            LatticeValue left = evaluateConstOrId(minus.f0, state);
            LatticeValue right = evaluateConstOrId(minus.f2, state);
            return binaryOperation(left, "-", right);
        }
        else if (choice instanceof TimesExpression) {
            TimesExpression times = (TimesExpression) choice;
            LatticeValue left = evaluateConstOrId(times.f0, state);
            LatticeValue right = evaluateConstOrId(times.f2, state);
            return binaryOperation(left, "*", right);
        }
        else if (choice instanceof CompareExpression) {
            CompareExpression cmp = (CompareExpression) choice;
            LatticeValue left = evaluateConstOrId(cmp.f0, state);
            LatticeValue right = evaluateConstOrId(cmp.f2, state);
            return binaryOperation(left, "<", right);
        }
        
        // DEFAULT - Unknown expression type
        return LatticeValue.top();
    }
    
    /**
     * Evaluate ConstOrId (can be IntegerLiteral, Identifier, TrueLiteral, FalseLiteral)
     * 
     * @param constOrId - ConstOrId node
     * @param state - Current lattice state
     * @return LatticeValue of the constant or identifier
     */
    private LatticeValue evaluateConstOrId(Node constOrId, Map<String, LatticeValue> state) {
        if (constOrId == null) {
            return LatticeValue.top();
        }
        
        // ConstOrId is a choice node (like Expression)
        if (constOrId instanceof syntaxtree.ConstOrId) {
            Object choice = ((syntaxtree.ConstOrId) constOrId).f0.choice;
            
            if (choice instanceof IntegerLiteral) {
                IntegerLiteral lit = (IntegerLiteral) choice;
                int value = extractIntegerValue(lit);
                return LatticeValue.intValue(value);
            }
            else if (choice instanceof TrueLiteral) {
                return LatticeValue.boolValue(true);
            }
            else if (choice instanceof FalseLiteral) {
                return LatticeValue.boolValue(false);
            }
            else if (choice instanceof Identifier) {
                String name = ((Identifier) choice).f0.tokenImage;
                return state.getOrDefault(name, LatticeValue.top());
            }
        }
        // Fallback for direct types
        else if (constOrId instanceof IntegerLiteral) {
            int value = extractIntegerValue((IntegerLiteral) constOrId);
            return LatticeValue.intValue(value);
        }
        else if (constOrId instanceof TrueLiteral) {
            return LatticeValue.boolValue(true);
        }
        else if (constOrId instanceof FalseLiteral) {
            return LatticeValue.boolValue(false);
        }
        else if (constOrId instanceof Identifier) {
            String name = ((Identifier) constOrId).f0.tokenImage;
            return state.getOrDefault(name, LatticeValue.top());
        }
        
        return LatticeValue.top();
    }
    
    /**
     * Extract integer value from IntegerLiteral
     * 
     * IntegerLiteral can be:
     *   - IntegerLiteralWithPosSign: "+" <INTEGER_LITERAL>
     *   - IntegerLiteralWithNegSign: "-" <INTEGER_LITERAL>
     *   - PlainIntegerLiteral: <INTEGER_LITERAL>
     * 
     * @param lit - IntegerLiteral to extract from
     * @return The integer value
     */
    private int extractIntegerValue(IntegerLiteral lit) {
        // The IntegerLiteral wraps one of three types
        if (lit.f0.choice instanceof PlainIntegerLiteral) {
            PlainIntegerLiteral plain = (PlainIntegerLiteral) lit.f0.choice;
            return parseInt(plain.f0.tokenImage);
        }
        else if (lit.f0.choice instanceof IntegerLiteralWithPosSign) {
            IntegerLiteralWithPosSign pos = (IntegerLiteralWithPosSign) lit.f0.choice;
            // Format: "+" <INTEGER>
            return parseInt(pos.f1.tokenImage);
        }
        else if (lit.f0.choice instanceof IntegerLiteralWithNegSign) {
            IntegerLiteralWithNegSign neg = (IntegerLiteralWithNegSign) lit.f0.choice;
            // Format: "-" <INTEGER>
            return -parseInt(neg.f1.tokenImage);
        }
        // Fallback
        return 0;
    }
    
    /**
     * Safe integer parsing
     * @param str - String to parse
     * @return Parsed integer, or 0 if parsing fails
     */
    private int parseInt(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * Evaluate method call (MessageSend) with MEET semantics for polymorphic calls
     * 
     * Use the method's summary to get its return value in context-insensitive analysis.
     * The return value is MEET of all possible returns from the method.
     * 
     * Algorithm:
     * 1. Extract method name from MessageSend
     * 2. Determine object type from primary expression
     * 3. Get all child classes (polymorphic overrides)
     * 4. Collect return values from all overrides
     * 5. MEET semantics: if all same constant, return that; otherwise return T
     * 
     * @param msgSend - The message send (method call) expression
     * @param state - Current lattice state (used to lookup variable types)
     * @return Return value from the method's summary with MEET semantics, or T if unknown
     */
    private LatticeValue evaluateMessageSend(MessageSend msgSend, Map<String, LatticeValue> state) {
        try {
            // Extract method name (MessageSend.f2 = Identifier)
            Identifier methodIdent = msgSend.f2;
            String methodName = methodIdent.f0.tokenImage;
            
            // Extract primary expression (MessageSend.f0)
            PrimaryExpression primaryExpr = msgSend.f0;
            Object primaryChoice = primaryExpr.f0.choice;
            
            String objectType = null;
            
            // Case 1: this.method() - use current class if available
            if (primaryChoice instanceof ThisExpression) {
                // In context-insensitive analysis, treated as unknown
                // Return TOP to be conservative
                return LatticeValue.top();
            }
            
            // Case 2: obj.method() - determine type from identifier
            else if (primaryChoice instanceof Identifier) {
                Identifier objIdent = (Identifier) primaryChoice;
                String objName = objIdent.f0.tokenImage;
                
                // Look up object type from SymbolTable
                if (SymbolTable.class_map.containsKey(objName)) {
                    objectType = objName;
                } else {
                    // Variable name - would need type info, conservatively return T
                    return LatticeValue.top();
                }
            }
            
            // Other cases (allocation, array, etc.) - not callable
            else {
                return LatticeValue.top();
            }
            
            // If we couldn't determine the type, return TOP
            if (objectType == null) {
                return LatticeValue.top();
            }
            
            // Get all child classes (including objectType itself)
            Set<String> childClasses = new HashSet<>();
            childClasses.add(objectType);
            
            // Add direct children from classChildren map
            if (SymbolTable.classChildren.containsKey(objectType)) {
                childClasses.addAll(SymbolTable.classChildren.get(objectType));
            }
            
            // Collect return values from all overrides
            LatticeValue meetValue = null;
            boolean allReturnSame = true;
            
            for (String childClass : childClasses) {
                // Construct method signature: ClassName.methodName
                String methodSig = childClass + "." + methodName;
                
                // Look up method summary
                MethodSummary summary = constantPropagation.methodSummaries.get(methodSig);
                
                if (summary == null) {
                    // Method doesn't exist in this class, conservatively return T
                    return LatticeValue.top();
                }
                
                LatticeValue returnVal = summary.getReturnValue();
                
                // Check if return value is constant
                if (returnVal == null || returnVal.isTop()) {
                    // At least one override returns TOP (unknown)
                    return LatticeValue.top();
                }
                
                // First override - initialize meet value
                if (meetValue == null) {
                    meetValue = returnVal;
                }
                // Check if all overrides return same constant
                else if (!meetValue.equals(returnVal)) {
                    // Different constants - MEET fails
                    return LatticeValue.top();
                }
            }
            
            // All overrides return same constant - use that value
            if (meetValue != null && meetValue.isConstant()) {
                return meetValue;
            }
            
            return LatticeValue.top();
            
        } catch (Exception e) {
            // Any error during method resolution - conservatively return TOP
            return LatticeValue.top();
        }
    }
    
    /**
     * Evaluate binary operations with priority: ⊥ > constants > T
     * 
     * @param left - Left operand
     * @param op - Operator (+, -, *, <)
     * @param right - Right operand
     * @return Result following lattice priority rules
     */
    private LatticeValue binaryOperation(LatticeValue left, String op, LatticeValue right) {
        
        // PRIORITY 1: If either operand is ⊥ (bottom/contradiction), result is ⊥
        if (left.isBottom() || right.isBottom()) {
            return LatticeValue.bottom();
        }
        
        // PRIORITY 2: If both operands are constants, compute result
        if (left.isConstant() && right.isConstant()) {
            
            // Arithmetic operations (+, -, *)
            if (op.equals("+") || op.equals("-") || op.equals("*")) {
                // Both must be integers
                if (left.getType() == LatticeValue.Type.INT && right.getType() == LatticeValue.Type.INT) {
                    int l = left.getIntValue();
                    int r = right.getIntValue();
                    
                    if (op.equals("+")) {
                        return LatticeValue.intValue(l + r);
                    } else if (op.equals("-")) {
                        return LatticeValue.intValue(l - r);
                    } else { // "*"
                        return LatticeValue.intValue(l * r);
                    }
                } else {
                    // Type error - can't add bool to int
                    return LatticeValue.top();
                }
            }
            
            // Comparison (<)
            else if (op.equals("<")) {
                // Both must be integers
                if (left.getType() == LatticeValue.Type.INT && right.getType() == LatticeValue.Type.INT) {
                    int l = left.getIntValue();
                    int r = right.getIntValue();
                    return LatticeValue.boolValue(l < r);
                } else {
                    // Type error - can't compare bool to int
                    return LatticeValue.top();
                }
            }
        }
        
        // PRIORITY 3: If neither is ⊥ but at least one is T, result is T (unknown propagates)
        return LatticeValue.top();
    }
    
    /**
     * Evaluate unary operations with priority: ⊥ > constants > T
     * 
     * Currently only supports ! (logical not)
     * 
     * @param op - Operator (!)
     * @param operand - Operand value
     * @return Result following lattice priority rules
     */
    private LatticeValue unaryOperation(String op, LatticeValue operand) {
        
        // PRIORITY 1: If operand is ⊥, result is ⊥
        if (operand.isBottom()) {
            return LatticeValue.bottom();
        }
        
        // Only ! operator supported
        if (op.equals("!")) {
            
            // PRIORITY 2: If operand is constant boolean, compute result
            if (operand.getType() == LatticeValue.Type.BOOLEAN) {
                boolean value = operand.getBoolValue();
                return LatticeValue.boolValue(!value);
            }
            
            // PRIORITY 3: If operand is not a boolean, type error = T
            return LatticeValue.top();
        }
        
        // Unknown operator
        return LatticeValue.top();
    }
    
    /**
     * Helper: Check if a value is constant (INT or BOOL)
     * @param value - LatticeValue to check
     * @return true if value is INT or BOOL constant
     */
    public boolean isConstant(LatticeValue value) {
        return value.isConstant();
    }
    
    /**
     * Helper: Check if a value is unpredictable (BOTTOM)
     * @param value - LatticeValue to check
     * @return true if value is BOTTOM (unpredictable/conflicted)
     */
    public boolean isUnpredictable(LatticeValue value) {
        return value.isBottom();
    }
}
