package visitor;
import syntaxtree.*;
import java.util.*;

/**
 * MethodAnalyzer - Intra-procedural data flow analysis
 * 
 * Performs fixed-point iteration on a method's CFG:
 * 1. Initializes parameters from MethodSummary
 * 2. Initializes local variables to T (top/unknown)
 * 3. Runs WORKLIST-based fixed-point iteration
 * 4. Maintains IN/OUT maps for each CFG node (CRITICAL for Pass 5!)
 * 
 * Key invariant: FRESH maps for EACH method analysis
 * - Don't reuse old IN/OUT maps from previous analysis
 * - Merge points use MEET operation (⊓)
 * - Transfer function updates OUT from IN
 */
public class MethodAnalyzer {
    
    private ConstantPropagation constantPropagation;
    private ExpressionEvaluator exprEvaluator;
    
    // Current method being analyzed
    private String currentMethodSig;
    
    // CFG nodes with their live IN/OUT maps
    private Map<CFGNode, Map<String, LatticeValue>> nodeLiveIn;
    private Map<CFGNode, Map<String, LatticeValue>> nodeLiveOut;
    
    // Track reachability per edge (from->to)
    // Key: "fromId:toId", Value: reachable (true) or blocked (false)
    private Map<String, Boolean> edgeReachability;
    
    // Worklist for fixed-point iteration
    private Queue<CFGNode> nodeWorklist;
    
    // Track computed return value during analysis
    private LatticeValue computedReturnValue = LatticeValue.top();
    
    /**
     * Constructor
     * @param cp - ConstantPropagation orchestrator
     */
    public MethodAnalyzer(ConstantPropagation cp) {
        this.constantPropagation = cp;
        this.exprEvaluator = cp.getExpressionEvaluator();
        this.nodeLiveIn = new HashMap<>();
        this.nodeLiveOut = new HashMap<>();
        this.nodeWorklist = new LinkedList<>();
        this.edgeReachability = new HashMap<>();
    }
    
    /**
     * Analyze a single method - Main entry point
     * 
     * Algorithm:
     * 1. Get method's CFG and summary
     * 2. Initialize FRESH IN/OUT maps for all CFG nodes
     * 3. Set entry node IN with parameter values
     * 4. Initialize local variables to T
     * 5. Run fixed-point iteration
     * 6. Store final variable values for the method
     * 7. Return method's return value (from exit node)
     * 
     * CRITICAL: Create NEW maps each time (don't reuse old ones!)
     * 
     * @param methodSig - Method to analyze
     * @return LatticeValue representing the method's return value
     */
    public LatticeValue analyzeMethod(String methodSig) {
        this.currentMethodSig = methodSig;
        
        // Get the CFG for this method
        CFG cfg = constantPropagation.getMethodCFG(methodSig);
        if (cfg == null) {
            return LatticeValue.top();
        }
        
        // Step 1: Create FRESH IN/OUT maps (discarding any old ones!)
        nodeLiveIn.clear();
        nodeLiveOut.clear();
        nodeWorklist.clear();
        edgeReachability.clear();
        
        // Get method signature and summary
        MethodSummary summary = constantPropagation.methodSummaries.get(methodSig);
        if (summary == null) {
            return LatticeValue.top();
        }
        
        // Step 2: Initialize IN/OUT maps for all CFG nodes
        Set<CFGNode> allNodes = cfg.getNodes();
        
        for (CFGNode node : allNodes) {
            // Initialize IN and OUT as empty maps
            // Local variables will be initialized to TOP when first accessed
            nodeLiveIn.put(node, new HashMap<>());
            nodeLiveOut.put(node, new HashMap<>());
        }
        
        // Step 3: Set entry node with initial state
        CFGNode entryNode = cfg.getEntry();
        Map<String, LatticeValue> entryIn = nodeLiveIn.get(entryNode);
        
        // Entry IN contains parameter values from summary
        for (String param : summary.getParameterValues().keySet()) {
            entryIn.put(param, summary.getParameterValue(param));
        }
        
        // Step 4: Local variables initialized to T (top/unknown) when first accessed
        
        // Step 5: Run fixed-point iteration
        LatticeValue returnValue = fixedPointIteration(cfg);
        
        // Step 6: Store final values in method summary
        // (No longer storing in separate methodFinalStates - use MethodSummary directly)
        
        // Step 7: Return the computed return value
        return returnValue;
    }
    
    /**
     * Fixed-point iteration on CFG nodes
     * 
     * Algorithm:
     * 1. Initialize worklist with all CFG nodes
     * 2. While worklist not empty:
     *    a. Pop node N
     *    b. Compute IN = MEET of predecessors' OUT
     *    c. Compute OUT = transfer(N, IN)
     *    d. If OUT changed, add all successors to worklist
     * 3. Extract return value from exit node
     * 
     * Monotonicity: Values only become more precise, so after O(lattice-height)
     * iterations, fixed-point is reached.
     * 
     * @param cfg - Control flow graph to analyze
     * @return Return value of the method (from exit node)
     */
    private LatticeValue fixedPointIteration(CFG cfg) {
        Set<CFGNode> allNodes = cfg.getNodes();
        CFGNode exitNode = cfg.getExit();
        CFGNode entryNode = cfg.getEntry();
        
        // Initialize worklist with all nodes for convergence
        nodeWorklist.clear();
        nodeWorklist.addAll(allNodes);
        
        // Reset return value
        computedReturnValue = LatticeValue.top();
        
        // Initialize all edges as reachable
        edgeReachability.clear();
        for (CFGNode node : allNodes) {
            for (CFGNode succ : node.getSuccessors()) {
                String edgeKey = node.getNodeId() + ":" + succ.getNodeId();
                edgeReachability.put(edgeKey, true);
            }
        }
        
        int iterations = 0;
        int maxIterations = allNodes.size() * 5;  // Safety bound
        
        while (!nodeWorklist.isEmpty() && iterations < maxIterations) {
            iterations++;
            
            CFGNode node = nodeWorklist.poll();
            
            // Get current IN map
            Map<String, LatticeValue> currentIn = nodeLiveIn.get(node);
            
            // Check if this node is reachable via any incoming edge
            // A node is unreachable if all its predecessor edges are blocked
            List<CFGNode> predecessors = node.getPredecessors();
            boolean hasReachableIncoming = false;
            if (predecessors.isEmpty()) {
                // ENTRY node or no predecessors - always reachable
                hasReachableIncoming = true;
            } else {
                for (CFGNode pred : predecessors) {
                    String edgeKey = pred.getNodeId() + ":" + node.getNodeId();
                    Boolean edgeReach = edgeReachability.get(edgeKey);
                    if (edgeReach != null && edgeReach) {
                        hasReachableIncoming = true;
                        break;
                    }
                }
            }
            
            // Skip if all incoming edges are blocked
            if (!hasReachableIncoming) {
                continue;
            }
            
            // Step 1: Compute IN by MEET-ing from REACHABLE predecessor edges
            if (!predecessors.isEmpty()) {
                for (CFGNode pred : predecessors) {
                    // Check if the edge pred->node is reachable
                    String edgeKey = pred.getNodeId() + ":" + node.getNodeId();
                    Boolean edgeReach = edgeReachability.get(edgeKey);
                    if (edgeReach == null || !edgeReach) {
                        // Edge is blocked - don't include in MEET
                        continue;
                    }
                    
                    // Edge is reachable - include in MEET
                    Map<String, LatticeValue> predOut = nodeLiveOut.get(pred);
                    if (predOut != null) {
                        for (String var : predOut.keySet()) {
                            LatticeValue predValue = predOut.get(var);
                            LatticeValue currentValue = currentIn.getOrDefault(var, LatticeValue.top());
                            
                            // MEET: currentValue = currentValue ⊓ predValue
                            LatticeValue meeted = LatticeValue.meet(currentValue, predValue);
                            currentIn.put(var, meeted);
                        }
                    }
                }
            }
            
            // Step 2: Compute OUT by applying transfer function
            Map<String, LatticeValue> newOut = transfer(node, currentIn);
            Map<String, LatticeValue> oldOut = nodeLiveOut.get(node);
            
            // [DEBUG] Node analysis details - commented out for cleaner output
            // System.out.println("[DEBUG] Node: " + node.getLabel() + ", oldOut: " + oldOut + ", newOut: " + newOut);
            
            // Step 3: Check if OUT changed
            boolean changed = !newOut.equals(oldOut);
            
            // ENTRY node always propagates (starting point - has no predecessors)
            if ("ENTRY".equals(node.getLabel())) {
                changed = true;
            }
            
            // Always update IN state (computed from predecessors)
            node.in = currentIn;
            
            if (changed) {
                // Update OUT in both local map and CFGNode
                nodeLiveOut.put(node, newOut);
                node.out = newOut;  // Update the CFGNode's out field for Pass 5
                
                // Step 4: Determine which successor edges are reachable
                String nodeType = node.getNodeType();
                List<CFGNode> successors = node.getSuccessors();
                
                // Handle control flow nodes: if, while, for
                if (("if".equals(nodeType) || "while".equals(nodeType) || "for".equals(nodeType)) && 
                    successors.size() == 2) {
                    
                    // Extract condition variable and evaluate
                    String condVarName = null;
                    if ("if".equals(nodeType)) {
                        Node controlFlow = node.getControlFlowNode();
                        if (controlFlow instanceof IfStatement) {
                            IfStatement ifStmt = (IfStatement) controlFlow;
                            Identifier condIdent = ifStmt.f2;
                            condVarName = condIdent.f0.tokenImage;
                        }
                    } else if ("while".equals(nodeType)) {
                        Node controlFlow = node.getControlFlowNode();
                        if (controlFlow instanceof WhileStatement) {
                            WhileStatement whileStmt = (WhileStatement) controlFlow;
                            Identifier condIdent = whileStmt.f2;
                            condVarName = condIdent.f0.tokenImage;
                        }
                    } else if ("for".equals(nodeType)) {
                        Node controlFlow = node.getControlFlowNode();
                        // For loop conditions are usually complex expressions, not simple identifiers
                        // We'll treat for loops as always having both branches reachable
                        // A full solution would need to evaluate complex expressions
                    }
                    
                    if (condVarName != null) {
                        LatticeValue condValue = newOut.getOrDefault(condVarName, LatticeValue.top());
                        
                        if (condValue.isBottom()) {
                            // BOTTOM predicate: both branches unreachable (don't process successors)
                            CFGNode succTrue = successors.get(0);
                            CFGNode succFalse = successors.get(1);
                            String edgeKeyTrue = node.getNodeId() + ":" + succTrue.getNodeId();
                            String edgeKeyFalse = node.getNodeId() + ":" + succFalse.getNodeId();
                            edgeReachability.put(edgeKeyTrue, false);
                            edgeReachability.put(edgeKeyFalse, false);
                        } else if (condValue.isConstant() && condValue.getType() == LatticeValue.Type.BOOLEAN) {
                            // Constant boolean: one branch reachable, one unreachable
                            boolean isTrue = condValue.getBoolValue();
                            CFGNode succTrue = successors.get(0);
                            CFGNode succFalse = successors.get(1);
                            String edgeKeyTrue = node.getNodeId() + ":" + succTrue.getNodeId();
                            String edgeKeyFalse = node.getNodeId() + ":" + succFalse.getNodeId();
                            
                            if (isTrue) {
                                edgeReachability.put(edgeKeyTrue, true);
                                edgeReachability.put(edgeKeyFalse, false);
                                if (!nodeWorklist.contains(succTrue)) {
                                    nodeWorklist.add(succTrue);
                                }
                            } else {
                                edgeReachability.put(edgeKeyTrue, false);
                                edgeReachability.put(edgeKeyFalse, true);
                                if (!nodeWorklist.contains(succFalse)) {
                                    nodeWorklist.add(succFalse);
                                }
                            }
                        } else {
                            // TOP or unknown: both branches reachable
                            CFGNode succTrue = successors.get(0);
                            CFGNode succFalse = successors.get(1);
                            String edgeKeyTrue = node.getNodeId() + ":" + succTrue.getNodeId();
                            String edgeKeyFalse = node.getNodeId() + ":" + succFalse.getNodeId();
                            edgeReachability.put(edgeKeyTrue, true);
                            edgeReachability.put(edgeKeyFalse, true);
                            
                            for (CFGNode succ : successors) {
                                if (!nodeWorklist.contains(succ)) {
                                    nodeWorklist.add(succ);
                                }
                            }
                        }
                    } else {
                        // Couldn't extract condition - assume both branches reachable
                        for (CFGNode succ : successors) {
                            String edgeKey = node.getNodeId() + ":" + succ.getNodeId();
                            edgeReachability.put(edgeKey, true);
                            if (!nodeWorklist.contains(succ)) {
                                nodeWorklist.add(succ);
                            }
                        }
                    }
                } else {
                    // Not a conditional node - all successors reachable
                    for (CFGNode succ : successors) {
                        String edgeKey = node.getNodeId() + ":" + succ.getNodeId();
                        edgeReachability.put(edgeKey, true);
                        if (!nodeWorklist.contains(succ)) {
                            nodeWorklist.add(succ);
                        }
                    }
                }
            }
        }
        
        // Mark unreachable nodes after convergence
        // A node is unreachable if all its incoming edges are blocked
        for (CFGNode node : allNodes) {
            if ("ENTRY".equals(node.getLabel())) {
                continue;  // ENTRY is always reachable
            }
            
            List<CFGNode> preds = node.getPredecessors();
            if (preds.isEmpty()) {
                continue;  // No predecessors but not ENTRY - assume reachable
            }
            
            boolean hasReachableEdge = false;
            for (CFGNode pred : preds) {
                String edgeKey = pred.getNodeId() + ":" + node.getNodeId();
                Boolean edgeReach = edgeReachability.get(edgeKey);
                if (edgeReach != null && edgeReach) {
                    hasReachableEdge = true;
                    break;
                }
            }
            
            // Mark node as unreachable (for dead code elimination tracking)
            if (!hasReachableEdge) {
                node.setUnreachable(true);
            }
        }
        
        // Return the computed return value
        return computedReturnValue;
    }
    
    /**
     * Transfer function - Apply dataflow for one CFG node
     * 
     * Updates OUT based on IN and the node's statement:
     * - Assignment: x = expr → OUT[x] = evaluate(expr, IN); also check for method calls
     * - VoidMessageSend: check for inter-procedural argument passing
     * - Return: Track return value
     * - Other statements: OUT = copy(IN)
     * 
     * @param node - CFG node being processed
     * @param in - IN state (variables entering this node)
     * @return OUT state (variables after this node)
     */
    private Map<String, LatticeValue> transfer(CFGNode node, Map<String, LatticeValue> in) {
        // Copy IN to OUT (default: statements don't change variables)
        Map<String, LatticeValue> out = new HashMap<>(in);
        
        // SPECIAL HANDLING: Return statement
        if ("return".equals(node.getNodeType())) {
            String returnVar = node.getReturnVariable();
            if (returnVar != null) {
                LatticeValue returnedValue = in.getOrDefault(returnVar, LatticeValue.top());
                // Directly use returned value (no MEET)
                if (!returnedValue.equals(computedReturnValue)) {
                    computedReturnValue = returnedValue;
                    // System.out.println("      [Return] " + node.getLabel() + " returns " + returnVar + 
                    //                  " = " + returnedValue.getType());
                }
            }
            return out;
        }
        
        // Get the statement from this node and analyze it
        List<Statement> stmts = node.getStatements();
        if (stmts != null && !stmts.isEmpty()) {
            Statement stmt = stmts.get(0);  // Take first statement
            
            if (stmt != null && stmt.f0 != null) {
                Object choice = stmt.f0.choice;
                
                // ASSIGNMENT: x = expr
                if (choice instanceof AssignmentStatement) {
                    AssignmentStatement assign = (AssignmentStatement) choice;
                    String varName = assign.f0.f0.tokenImage;
                    
                    // Check if RHS is a method call
                    Expression rhs = assign.f2;
                    handleMethodCall(rhs, in);
                    
                    // Evaluate RHS expression
                    LatticeValue rhsValue = evaluateExpression(rhs, in);
                    
                    // Update the variable in OUT
                    out.put(varName, rhsValue);
                    
                    // DEBUG: Print what was discovered
                    // System.out.println("    [Transfer] " + node.getLabel() + ": " + varName + 
                    //                  " = " + rhsValue.getType() + 
                    //                  (rhsValue.isConstant() ? " (" + (rhsValue.getType() == LatticeValue.Type.INT ? rhsValue.getIntValue() : rhsValue.getBoolValue()) + ")" : ""));
                }
                // VOID MESSAGE SEND (method call with no assignment)
                else if (choice instanceof VoidMessageSendStatement) {
                    VoidMessageSendStatement voidMsg = (VoidMessageSendStatement) choice;
                    MessageSend msgSend = voidMsg.f0;
                    handleMethodCallMessageSend(msgSend, in);
                }
                // For other statements, just propagate the IN state
            }
        }
        
        return out;
    }
    
    /**
     * Handle inter-procedural argument passing for method calls in expressions
     * When we see: var = obj.method(args), extract argument values and update called method summary
     */
    private void handleMethodCall(Expression expr, Map<String, LatticeValue> in) {
        if (expr == null) return;
        
        Object choice = expr.f0.choice;
        
        // Check if this is a MessageSend
        if (choice instanceof MessageSend) {
            MessageSend msgSend = (MessageSend) choice;
            handleMethodCallMessageSend(msgSend, in);
        }
    }
    
    /**
     * Handle a MessageSend to update called method's parameter values
     * MessageSend: f0->PrimaryExpression (object) f1->"." f2->Identifier (method) f3->"(" f4->ArgList f5->")"
     */
    private void handleMethodCallMessageSend(MessageSend msgSend, Map<String, LatticeValue> in) {
        if (msgSend == null) return;
        
        // Get method name from f2
        if (msgSend.f2 == null || msgSend.f2.f0 == null) return;
        String methodName = msgSend.f2.f0.tokenImage;
        
        // Get arguments from f4 (ArgList)
        List<LatticeValue> argValues = new ArrayList<>();
        if (msgSend.f4.present()) {
            ArgList argList = (ArgList) msgSend.f4.node;
            if (argList != null) {
                // ArgList: f0->ConstOrId f1->ArgRest
                argValues.add(evaluateConstOrId(argList.f0, in));
                
                // ArgRest: (f0->"," f1->ConstOrId)*
                if (argList.f1.present()) {
                    Enumeration<Node> rests = argList.f1.elements();
                    while (rests.hasMoreElements()) {
                        ArgRest rest = (ArgRest) rests.nextElement();
                        argValues.add(evaluateConstOrId(rest.f1, in));
                    }
                }
            }
        }
        
        // Extract object's declared type from the expression
        // msgSend.f0 is the PrimaryExpression (the object)
        PrimaryExpression objExpr = (PrimaryExpression) msgSend.f0;
        String objectType = getObjectType(objExpr);
        
        if (objectType == null) {
            // Conservative: if we can't determine type, don't call anything
            return;
        }
        
        // Find all classes that ARE this type OR EXTEND it
        Set<String> possibleClasses = findClassesWithType(objectType);
        
        // Filter to only classes that have this method
        Set<String> classesWithMethod = new HashSet<>();
        for (String className : possibleClasses) {
            Set<String> classMethods = SymbolTable.classMethodsMap.getOrDefault(className, new HashSet<>());
            if (classMethods.contains(methodName)) {
                classesWithMethod.add(className);
            }
        }
        
        // Update parameters for all possible methods
        for (String calledClass : classesWithMethod) {
            String calledMethodSig = calledClass + "." + methodName;
            
            // Get the method summary and update its parameters
            MethodSummary calledSummary = constantPropagation.methodSummaries.get(calledMethodSig);
            if (calledSummary == null) continue;
            
            // Get parameter names from the method (need CFG to get this)
            CFG calledCFG = constantPropagation.getMethodCFG(calledMethodSig);
            if (calledCFG == null) continue;
            
            // Get parameter list from method
            List<String> paramNames = calledCFG.getParameterNames();
            
            // Update parameter values in the summary
            boolean improved = false;
            for (int i = 0; i < paramNames.size() && i < argValues.size(); i++) {
                String paramName = paramNames.get(i);
                LatticeValue argValue = argValues.get(i);
                
                // MEET with existing parameter value
                LatticeValue oldValue = calledSummary.getParameterValue(paramName);
                LatticeValue newValue = LatticeValue.meet(oldValue, argValue);
                
                if (!newValue.equals(oldValue)) {
                    calledSummary.setParameterValue(paramName, newValue);
                    improved = true;
                    // System.out.println("    [InterProc] " + calledMethodSig + "." + paramName + 
                    //                  " improved: " + oldValue.getType() + " -> " + newValue.getType());
                }
            }
            
            // If parameters improved, add method to worklist
            if (improved) {
                constantPropagation.addMethodToWorklist(calledMethodSig);
            }
        }
    }
    
    /**
     * Extract the declared type of an object from its PrimaryExpression
     * E.g., if expression is variable "a", returns the type of "a"
     * 
     * @param objExpr - PrimaryExpression representing the object
     * @return Type name (class name), or null if cannot determine
     */
    private String getObjectType(PrimaryExpression objExpr) {
        if (objExpr == null) return null;
        
        Object primaryChoice = objExpr.f0.choice;
        
        // Case: variable name (e.g., "x" in "x.foo()")
        if (primaryChoice instanceof Identifier) {
            String varName = ((Identifier) primaryChoice).f0.tokenImage;
            return getVariableType(varName);
        }
        
        // Case: "this" keyword
        if (primaryChoice instanceof ThisExpression) {
            // Extract class name from currentMethodSig (e.g., "Base.doWork" -> "Base")
            String[] parts = currentMethodSig.split("\\.");
            if (parts.length >= 1) {
                return parts[0];
            }
        }
        
        // Other cases: allocation expressions, etc. - return null (unknown type)
        return null;
    }
    
    /**
     * Get the declared type of a variable in the current method
     * Looks in method parameters and local variables
     * 
     * @param varName - Variable name
     * @return Type name (class name), or null if not found
     */
    private String getVariableType(String varName) {
        // Extract class name from currentMethodSig
        String[] parts = currentMethodSig.split("\\.");
        if (parts.length < 2) return null;
        
        String className = parts[0];
        String methodName = parts[1];
        
        // Get class data
        class_data classData = SymbolTable.class_map.get(className);
        if (classData == null) return null;
        
        // Get method data
        Method_data methodData = classData.m_methods.get(methodName);
        if (methodData == null) return null;
        
        // Check method parameters
        if (methodData.m_args.containsKey(varName)) {
            return methodData.m_args.get(varName).data_type;
        }
        
        // Check local variables
        if (methodData.m_vars.containsKey(varName)) {
            return methodData.m_vars.get(varName).data_type;
        }
        
        // Check class fields
        if (classData.m_fields.containsKey(varName)) {
            return classData.m_fields.get(varName).data_type;
        }
        
        return null;
    }
    
    /**
     * Find all classes that ARE a given type OR INHERIT from it
     * E.g., if type is "Base", return {"Base", "Child1", "Child2"} if they extend Base
     * 
     * @param type - Type name (class name)
     * @return Set of class names (the type itself + all subclasses)
     */
    private Set<String> findClassesWithType(String type) {
        Set<String> result = new HashSet<>();
        
        // Add the type itself
        if (SymbolTable.class_map.containsKey(type)) {
            result.add(type);
        }
        
        // Add all children (subclasses)
        Set<String> children = SymbolTable.classChildren.getOrDefault(type, new HashSet<>());
        result.addAll(children);
        
        // Recursively add children of children
        for (String child : children) {
            result.addAll(findClassesWithType(child));
        }
        
        return result;
    }
    
    /**
     * Evaluate an expression to a LatticeValue using constant propagation
     * 
     * @param expr - Expression to evaluate
     * @param constants - Variable constants available at this point
     * @return LatticeValue of the expression
     */
    private LatticeValue evaluateExpression(Expression expr, Map<String, LatticeValue> constants) {
        if (expr == null) {
            return LatticeValue.top();
        }
        
        Object choice = expr.f0.choice;
        
        // PrimaryExpression - unwrap it
        if (choice instanceof PrimaryExpression) {
            PrimaryExpression primary = (PrimaryExpression) choice;
            Object primaryChoice = primary.f0.choice;
            
            if (primaryChoice instanceof IntegerLiteral) {
                int value = extractIntegerValue((IntegerLiteral) primaryChoice);
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
                return constants.getOrDefault(name, LatticeValue.top());
            }
            else if (primaryChoice instanceof AllocationExpression) {
                return LatticeValue.top();  // Unknown value from allocation
            }
            else if (primaryChoice instanceof ArrayAllocationExpression) {
                return LatticeValue.top();  // Unknown value from array allocation
            }
            else {
                return LatticeValue.top();
            }
        }
        
        // INTEGER LITERAL (direct)
        else if (choice instanceof IntegerLiteral) {
            int value = extractIntegerValue((IntegerLiteral) choice);
            return LatticeValue.intValue(value);
        }
        // BOOLEAN LITERAL (direct)
        else if (choice instanceof TrueLiteral) {
            return LatticeValue.boolValue(true);
        }
        else if (choice instanceof FalseLiteral) {
            return LatticeValue.boolValue(false);
        }
        // IDENTIFIER (direct)
        else if (choice instanceof Identifier) {
            String name = ((Identifier) choice).f0.tokenImage;
            return constants.getOrDefault(name, LatticeValue.top());
        }
        // BINARY OPERATIONS
        else if (choice instanceof CompareExpression) {
            CompareExpression comp = (CompareExpression) choice;
            LatticeValue left = evaluateConstOrId(comp.f0, constants);
            LatticeValue right = evaluateConstOrId(comp.f2, constants);
            
            if (left.isConstant() && right.isConstant()) {
                int lVal = left.getIntValue();
                int rVal = right.getIntValue();
                return LatticeValue.boolValue(lVal < rVal);
            }
            return LatticeValue.top();
        }
        else if (choice instanceof PlusExpression) {
            PlusExpression plus = (PlusExpression) choice;
            LatticeValue left = evaluateConstOrId(plus.f0, constants);
            LatticeValue right = evaluateConstOrId(plus.f2, constants);
            
            if (left.isConstant() && right.isConstant() && 
                left.getType() == LatticeValue.Type.INT && 
                right.getType() == LatticeValue.Type.INT) {
                return LatticeValue.intValue(left.getIntValue() + right.getIntValue());
            }
            return LatticeValue.top();
        }
        else if (choice instanceof MinusExpression) {
            MinusExpression minus = (MinusExpression) choice;
            LatticeValue left = evaluateConstOrId(minus.f0, constants);
            LatticeValue right = evaluateConstOrId(minus.f2, constants);
            
            if (left.isConstant() && right.isConstant() && 
                left.getType() == LatticeValue.Type.INT && 
                right.getType() == LatticeValue.Type.INT) {
                return LatticeValue.intValue(left.getIntValue() - right.getIntValue());
            }
            return LatticeValue.top();
        }
        else if (choice instanceof TimesExpression) {
            TimesExpression times = (TimesExpression) choice;
            LatticeValue left = evaluateConstOrId(times.f0, constants);
            LatticeValue right = evaluateConstOrId(times.f2, constants);
            
            if (left.isConstant() && right.isConstant() && 
                left.getType() == LatticeValue.Type.INT && 
                right.getType() == LatticeValue.Type.INT) {
                return LatticeValue.intValue(left.getIntValue() * right.getIntValue());
            }
            return LatticeValue.top();
        }
        // METHOD CALLS - look up return value from method summaries
        else if (choice instanceof MessageSend) {
            MessageSend msgSend = (MessageSend) choice;
            return evaluateMessageSendReturn(msgSend, constants);
        }
        // OTHER EXPRESSIONS - all unknown
        else {
            return LatticeValue.top();
        }
    }
    
    /**
     * Evaluate a MessageSend by looking up the called method's return value
     * Handles polymorphic calls by finding all possible implementations
     * 
     * @param msgSend - MessageSend to evaluate
     * @param constants - Variable constants available at this point
     * @return Return value of the method (from methodSummaries) or TOP if unknown
     */
    private LatticeValue evaluateMessageSendReturn(MessageSend msgSend, Map<String, LatticeValue> constants) {
        try {
            // Extract method name from f2 (Identifier)
            Identifier methodIdent = msgSend.f2;
            String methodName = methodIdent.f0.tokenImage;
            
            // Get object type from f0 (PrimaryExpression)
            String objectType = getObjectType(msgSend.f0);
            if (objectType == null) {
                return LatticeValue.top();  // Unknown object type
            }
            
            // Find all classes that have this type (object type + all subclasses)
            Set<String> possibleClasses = findClassesWithType(objectType);
            
            // Collect return values from all possible implementations
            LatticeValue mergedReturn = LatticeValue.top();
            for (String className : possibleClasses) {
                String methodSig = className + "." + methodName;
                MethodSummary summary = constantPropagation.methodSummaries.get(methodSig);
                if (summary != null) {
                    // MEET the return value with what we've seen so far
                    mergedReturn = LatticeValue.meet(mergedReturn, summary.returnValue);
                }
            }
            
            return mergedReturn;
        } catch (Exception e) {
            return LatticeValue.top();
        }
    }
    
    /**
     * Evaluate a ConstOrId to a LatticeValue
     * 
     * @param constOrId - ConstOrId to evaluate
     * @param constants - Variable constants available at this point
     * @return LatticeValue of the ConstOrId
     */
    private LatticeValue evaluateConstOrId(ConstOrId constOrId, Map<String, LatticeValue> constants) {
        if (constOrId == null) {
            return LatticeValue.top();
        }
        
        Object choice = constOrId.f0.choice;
        
        // INTEGER LITERAL
        if (choice instanceof IntegerLiteral) {
            int value = extractIntegerValue((IntegerLiteral) choice);
            return LatticeValue.intValue(value);
        }
        // IDENTIFIER
        else if (choice instanceof Identifier) {
            String name = ((Identifier) choice).f0.tokenImage;
            return constants.getOrDefault(name, LatticeValue.top());
        }
        else {
            return LatticeValue.top();
        }
    }
    
    /**
     * Extract integer value from IntegerLiteral node
     */
    private int extractIntegerValue(IntegerLiteral intLit) {
        Object choice = intLit.f0.choice;
        
        if (choice instanceof PlainIntegerLiteral) {
            PlainIntegerLiteral plain = (PlainIntegerLiteral) choice;
            return Integer.parseInt(plain.f0.tokenImage);
        }
        else if (choice instanceof IntegerLiteralWithPosSign) {
            IntegerLiteralWithPosSign pos = (IntegerLiteralWithPosSign) choice;
            return Integer.parseInt(pos.f1.tokenImage);
        }
        else if (choice instanceof IntegerLiteralWithNegSign) {
            IntegerLiteralWithNegSign neg = (IntegerLiteralWithNegSign) choice;
            return -Integer.parseInt(neg.f1.tokenImage);
        }
        
        return 0;
    }
    
    /**
     * Get the IN map for a CFG node
     * Used by Pass 5 for constant propagation
     * 
     * @param node - CFG node
     * @return IN map (variables at entry to node)
     */
    public Map<String, LatticeValue> getNodeIn(CFGNode node) {
        return nodeLiveIn.getOrDefault(node, new HashMap<>());
    }
    
    /**
     * Get the OUT map for a CFG node
     * Used by Pass 5 for constant propagation
     * 
     * @param node - CFG node
     * @return OUT map (variables at exit from node)
     */
    public Map<String, LatticeValue> getNodeOut(CFGNode node) {
        return nodeLiveOut.getOrDefault(node, new HashMap<>());
    }
    
    /**
     * Set node's IN map (for initialization or testing)
     */
    public void setNodeIn(CFGNode node, Map<String, LatticeValue> in) {
        nodeLiveIn.put(node, new HashMap<>(in));
    }
    
    /**
     * Set node's OUT map (for initialization or testing)
     */
    public void setNodeOut(CFGNode node, Map<String, LatticeValue> out) {
        nodeLiveOut.put(node, new HashMap<>(out));
    }
}
