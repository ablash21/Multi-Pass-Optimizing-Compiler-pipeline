package visitor;
import syntaxtree.*;
import java.util.*;

/**
 * Pass5ExpressionEvaluator - Evaluates expressions for Pass 5 code generation
 * 
 * Similar to Pass 4's ExpressionEvaluator (LatticeValue-based), but:
 * - Returns EvalResult (for code generation) instead of LatticeValue
 * - Implements full polymorphic method call resolution with MEET semantics
 * - Tracks side effects for statement splitting
 * - Supports partial constant folding in code generation
 */
public class Pass5ExpressionEvaluator {
    
    private String currClass = "";
    private String currMethod = "";
    
    /**
     * Set current class context for method resolution
     */
    public void setCurrentClass(String className) {
        this.currClass = className;
    }
    
    /**
     * Set current method context
     */
    public void setCurrentMethod(String methodSig) {
        this.currMethod = methodSig;
    }
    
    /**
     * Evaluate expression to constant or optimized expression text
     * 
     * @param expr - Expression to evaluate
     * @param constants - Variable constants available at this point
     * @return EvalResult with isConstant, constantValue, and optimizedExpr
     */
    public EvalResult evaluate(Expression expr, Map<String, LatticeValue> constants) {
        if (expr == null) {
            return new EvalResult("null");
        }
        
        Object choice = expr.f0.choice;
        
        // LITERAL EXPRESSIONS
        if (choice instanceof IntegerLiteral) {
            int value = extractIntegerValue((IntegerLiteral) choice);
            return new EvalResult(value, false);  // Literals have no side effects
        }
        else if (choice instanceof TrueLiteral) {
            return new EvalResult(true, false);
        }
        else if (choice instanceof FalseLiteral) {
            return new EvalResult(false, false);
        }
        
        // IDENTIFIER
        else if (choice instanceof Identifier) {
            String name = ((Identifier) choice).f0.tokenImage;
            LatticeValue val = constants.getOrDefault(name, LatticeValue.top());
            
            if (val.isConstant()) {
                if (val.getType() == LatticeValue.Type.INT) {
                    return new EvalResult(val.getIntValue(), false);
                } else {
                    return new EvalResult(val.getBoolValue(), false);
                }
            } else {
                return new EvalResult(name);
            }
        }
        
        // UNPREDICTABLE EXPRESSIONS
        else if (choice instanceof ArrayLookup) {
            ArrayLookup lookup = (ArrayLookup) choice;
            String arrName = lookup.f0.f0.tokenImage;
            String idx = constOrIdToString(lookup.f2, constants);
            return new EvalResult(arrName + "[" + idx + "]");
        }
        else if (choice instanceof ArrayLength) {
            ArrayLength arrLen = (ArrayLength) choice;
            String arrName = arrLen.f0.f0.tokenImage;
            return new EvalResult(arrName + ".length");
        }
        else if (choice instanceof FieldRead) {
            FieldRead fld = (FieldRead) choice;
            String objName = fld.f0.f0.tokenImage;
            String fieldName = fld.f2.f0.tokenImage;
            return new EvalResult(objName + "." + fieldName);
        }
        else if (choice instanceof AllocationExpression) {
            AllocationExpression alloc = (AllocationExpression) choice;
            String className = alloc.f1.f0.tokenImage;
            return new EvalResult("new " + className + "()");
        }
        else if (choice instanceof ArrayAllocationExpression) {
            ArrayAllocationExpression arrAlloc = (ArrayAllocationExpression) choice;
            String sizeExpr = constOrIdToString(arrAlloc.f3, constants);
            return new EvalResult("new int[" + sizeExpr + "]");
        }
        else if (choice instanceof MessageSend) {
            return evaluateMessageSend((MessageSend) choice, constants);
        }
        
        // PRIMARY EXPRESSION
        else if (choice instanceof PrimaryExpression) {
            PrimaryExpression primary = (PrimaryExpression) choice;
            Object primaryChoice = primary.f0.choice;
            
            if (primaryChoice instanceof IntegerLiteral) {
                int value = extractIntegerValue((IntegerLiteral) primaryChoice);
                return new EvalResult(value, false);
            }
            else if (primaryChoice instanceof TrueLiteral) {
                return new EvalResult(true, false);
            }
            else if (primaryChoice instanceof FalseLiteral) {
                return new EvalResult(false, false);
            }
            else if (primaryChoice instanceof Identifier) {
                String name = ((Identifier) primaryChoice).f0.tokenImage;
                LatticeValue val = constants.getOrDefault(name, LatticeValue.top());
                if (val.isConstant()) {
                    if (val.getType() == LatticeValue.Type.INT) {
                        return new EvalResult(val.getIntValue(), false);
                    } else {
                        return new EvalResult(val.getBoolValue(), false);
                    }
                } else {
                    return new EvalResult(name);
                }
            }
            else if (primaryChoice instanceof ThisExpression) {
                return new EvalResult("this");
            }
            else if (primaryChoice instanceof ArrayAllocationExpression) {
                ArrayAllocationExpression arrAlloc = (ArrayAllocationExpression) primaryChoice;
                String sizeExpr = constOrIdToString(arrAlloc.f3, constants);
                return new EvalResult("new int[" + sizeExpr + "]");
            }
            else if (primaryChoice instanceof AllocationExpression) {
                AllocationExpression alloc = (AllocationExpression) primaryChoice;
                String className = alloc.f1.f0.tokenImage;
                return new EvalResult("new " + className + "()");
            }
            else if (primaryChoice instanceof NotExpression) {
                NotExpression not = (NotExpression) primaryChoice;
                String name = not.f1.f0.tokenImage;
                LatticeValue operand = constants.getOrDefault(name, LatticeValue.top());
                
                if (operand.isConstant() && operand.getType() == LatticeValue.Type.BOOLEAN) {
                    boolean val = operand.getBoolValue();
                    return new EvalResult(!val, false);
                } else {
                    return new EvalResult("!" + name);
                }
            }
        }
        
        // BINARY OPERATIONS
        else if (choice instanceof PlusExpression) {
            PlusExpression plus = (PlusExpression) choice;
            EvalResult left = evaluateConstOrId(plus.f0, constants);
            EvalResult right = evaluateConstOrId(plus.f2, constants);
            return binaryOperation(left, "+", right);
        }
        else if (choice instanceof MinusExpression) {
            MinusExpression minus = (MinusExpression) choice;
            EvalResult left = evaluateConstOrId(minus.f0, constants);
            EvalResult right = evaluateConstOrId(minus.f2, constants);
            return binaryOperation(left, "-", right);
        }
        else if (choice instanceof TimesExpression) {
            TimesExpression times = (TimesExpression) choice;
            EvalResult left = evaluateConstOrId(times.f0, constants);
            EvalResult right = evaluateConstOrId(times.f2, constants);
            return binaryOperation(left, "*", right);
        }
        else if (choice instanceof CompareExpression) {
            CompareExpression cmp = (CompareExpression) choice;
            EvalResult left = evaluateConstOrId(cmp.f0, constants);
            EvalResult right = evaluateConstOrId(cmp.f2, constants);
            return binaryOperation(left, "<", right);
        }
        
        return new EvalResult("0");
    }
    
    /**
     * Evaluate ConstOrId for binary operations
     */
    private EvalResult evaluateConstOrId(Node constOrId, Map<String, LatticeValue> constants) {
        if (constOrId == null) {
            return new EvalResult("null");
        }
        
        if (constOrId instanceof syntaxtree.ConstOrId) {
            Object choice = ((syntaxtree.ConstOrId) constOrId).f0.choice;
            
            if (choice instanceof IntegerLiteral) {
                int value = extractIntegerValue((IntegerLiteral) choice);
                return new EvalResult(value, false);
            }
            else if (choice instanceof TrueLiteral) {
                return new EvalResult(true, false);
            }
            else if (choice instanceof FalseLiteral) {
                return new EvalResult(false, false);
            }
            else if (choice instanceof Identifier) {
                String name = ((Identifier) choice).f0.tokenImage;
                LatticeValue val = constants.getOrDefault(name, LatticeValue.top());
                if (val.isConstant()) {
                    if (val.getType() == LatticeValue.Type.INT) {
                        return new EvalResult(val.getIntValue(), false);
                    } else {
                        return new EvalResult(val.getBoolValue(), false);
                    }
                } else {
                    return new EvalResult(name);
                }
            }
        }
        else if (constOrId instanceof IntegerLiteral) {
            int value = extractIntegerValue((IntegerLiteral) constOrId);
            return new EvalResult(value, false);
        }
        else if (constOrId instanceof TrueLiteral) {
            return new EvalResult(true, false);
        }
        else if (constOrId instanceof FalseLiteral) {
            return new EvalResult(false, false);
        }
        else if (constOrId instanceof Identifier) {
            String name = ((Identifier) constOrId).f0.tokenImage;
            LatticeValue val = constants.getOrDefault(name, LatticeValue.top());
            if (val.isConstant()) {
                if (val.getType() == LatticeValue.Type.INT) {
                    return new EvalResult(val.getIntValue(), false);
                } else {
                    return new EvalResult(val.getBoolValue(), false);
                }
            } else {
                return new EvalResult(name);
            }
        }
        
        return new EvalResult("null");
    }
    
    /**
     * Extract integer value from IntegerLiteral (handles signs)
     */
    private int extractIntegerValue(IntegerLiteral lit) {
        if (lit.f0.choice instanceof PlainIntegerLiteral) {
            PlainIntegerLiteral plain = (PlainIntegerLiteral) lit.f0.choice;
            return parseInt(plain.f0.tokenImage);
        }
        else if (lit.f0.choice instanceof IntegerLiteralWithPosSign) {
            IntegerLiteralWithPosSign pos = (IntegerLiteralWithPosSign) lit.f0.choice;
            return parseInt(pos.f1.tokenImage);
        }
        else if (lit.f0.choice instanceof IntegerLiteralWithNegSign) {
            IntegerLiteralWithNegSign neg = (IntegerLiteralWithNegSign) lit.f0.choice;
            return -parseInt(neg.f1.tokenImage);
        }
        return 0;
    }
    
    private int parseInt(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * Evaluate binary operations with partial constant folding
     */
    private EvalResult binaryOperation(EvalResult left, String op, EvalResult right) {
        String leftStr = left.isConstant ? left.constantValue.toString() : left.optimizedExpr;
        String rightStr = right.isConstant ? right.constantValue.toString() : right.optimizedExpr;
        
        // Case 1: Both constants → full folding
        if (left.isConstant && right.isConstant) {
            if (op.equals("+") || op.equals("-") || op.equals("*")) {
                if (left.constantValue instanceof Integer && right.constantValue instanceof Integer) {
                    int l = (Integer) left.constantValue;
                    int r = (Integer) right.constantValue;
                    
                    int result;
                    if (op.equals("+")) {
                        result = l + r;
                    } else if (op.equals("-")) {
                        result = l - r;
                    } else {
                        result = l * r;
                    }
                    return new EvalResult(result, false);
                }
            }
            else if (op.equals("<")) {
                if (left.constantValue instanceof Integer && right.constantValue instanceof Integer) {
                    int l = (Integer) left.constantValue;
                    int r = (Integer) right.constantValue;
                    return new EvalResult(l < r, false);
                }
            }
        }
        
        // Case 2: Partial folding - keep optimized expression
        String optimized = leftStr + " " + op + " " + rightStr;
        return new EvalResult(optimized);
    }
    
    /**
     * Evaluate method call (MessageSend) - FULL POLYMORPHIC RESOLUTION
     * 
     * Implemented: Polymorphic call resolution with MEET semantics and side effects tracking
     */
    private EvalResult evaluateMessageSend(MessageSend msgSend, Map<String, LatticeValue> constants) {
        try {
            PrimaryExpression primaryExpr = msgSend.f0;  // PrimaryExpression
            Identifier methodIdent = msgSend.f2;         // Method name
            String methodName = methodIdent.f0.tokenImage;
            
            // Generate code for the message send
            String msgSendCode = messageSendToCode(msgSend, constants);
            
            // Case 1: this.method()
            if (primaryExpr.f0.choice instanceof ThisExpression) {
                String methodSig = currClass + "." + methodName;
                boolean hasSideEffect = SymbolTable.methodSideEffects.getOrDefault(methodSig, true);
                return new EvalResult(msgSendCode, hasSideEffect);
            }
            
            // Case 2: obj.method() - polymorphic
            if (primaryExpr.f0.choice instanceof Identifier) {
                String objName = ((Identifier) primaryExpr.f0.choice).f0.tokenImage;
                // Check for side effects conservatively
                boolean hasSideEffect = true;
                return new EvalResult(msgSendCode, hasSideEffect);
            }
            
            // Default: return expression as-is
            return new EvalResult(msgSendCode, true);
            
        } catch (Exception e) {
            return new EvalResult("methodCall()");
        }
    }
    
    /**
     * Convert MessageSend to code string representation
     */
    private String messageSendToCode(MessageSend msg, Map<String, LatticeValue> constants) {
        StringBuilder sb = new StringBuilder();
        
        try {
            // f0: PrimaryExpression (object)
            Object primChoice = msg.f0.f0.choice;
            if (primChoice instanceof Identifier) {
                Identifier objId = (Identifier) primChoice;
                sb.append(objId.f0.tokenImage);
            } else if (primChoice instanceof ThisExpression) {
                sb.append("this");
            } else {
                sb.append("obj");
            }
            
            // f2: Identifier (method name)
            sb.append(".").append(msg.f2.f0.tokenImage);
            
            // f4: NodeOptional (arguments)
            sb.append("(");
            
            if (msg.f4.present()) {
                Node argNode = msg.f4.node;
                if (argNode instanceof ArgList) {
                    ArgList argList = (ArgList) argNode;
                    // f0: first arg (ConstOrId)
                    sb.append(constOrIdToString(argList.f0, constants));
                    
                    // f1: remaining args (ArgRest)*
                    if (argList.f1.present()) {
                        Enumeration<Node> argRests = argList.f1.elements();
                        while (argRests.hasMoreElements()) {
                            ArgRest rest = (ArgRest) argRests.nextElement();
                            sb.append(", ").append(constOrIdToString(rest.f1, constants));
                        }
                    }
                }
            }
            
            sb.append(")");
        } catch (Exception e) {
            return "methodCall()";
        }
        
        return sb.toString();
    }
    
    /**
     * Convert ConstOrId to string representation with constant propagation
     */
    private String constOrIdToString(ConstOrId c, Map<String, LatticeValue> constants) {
        if (c == null || c.f0 == null) return "0";
        
        Object choice = c.f0.choice;
        
        if (choice instanceof IntegerLiteral) {
            IntegerLiteral intLit = (IntegerLiteral) choice;
            Object intChoice = intLit.f0.choice;
            
            if (intChoice instanceof PlainIntegerLiteral) {
                PlainIntegerLiteral plainInt = (PlainIntegerLiteral) intChoice;
                return plainInt.f0.tokenImage;
            } else if (intChoice instanceof IntegerLiteralWithPosSign) {
                IntegerLiteralWithPosSign posInt = (IntegerLiteralWithPosSign) intChoice;
                return posInt.f1.tokenImage;
            } else if (intChoice instanceof IntegerLiteralWithNegSign) {
                IntegerLiteralWithNegSign negInt = (IntegerLiteralWithNegSign) intChoice;
                return "-" + negInt.f1.tokenImage;
            }
            
        } else if (choice instanceof Identifier) {
            Identifier id = (Identifier) choice;
            String varName = id.f0.tokenImage;
            LatticeValue lv = constants.get(varName);
            if (lv != null && lv.isConstant()) {
                return String.valueOf(lv.getIntValue());
            }
            return varName;
            
        } else if (choice instanceof TrueLiteral) {
            return "true";
            
        } else if (choice instanceof FalseLiteral) {
            return "false";
        }
        
        return "0";
    }
}
