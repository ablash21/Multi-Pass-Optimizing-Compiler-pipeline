
package visitor;
import syntaxtree.*;
import java.util.*;

/**
 * Builds a Control Flow Graph from a list of statements
 * Groups consecutive non-control-flow statements into basic blocks
 * Creates branches for control-flow statements (if, while, for)
 */
public class CFGBuilder {
    private CFG cfg;
    
    // Map AST Statement to corresponding CFGNode (for optimization in pass5)
    private Map<Statement, CFGNode> stmtToCFGNode = new HashMap<>();
    
    public CFGBuilder(CFG cfg) {
        this.cfg = cfg;
    }
    
    /**
     * Get the mapping from AST Statement to CFGNode
     * Used by pass5 for optimization
     */
    public Map<Statement, CFGNode> getStatementToCFGNodeMap() {
        return stmtToCFGNode;
    }
    
    /**
     * Build CFG from a list of statements with optional return
     * @param statements The method body statements
     * @param returnNode The return ConstOrId (null for main methods)
     * Returns: the CFGNode chain; entry point is returned
     */
    public void buildCFG(NodeListOptional statements, Node returnNode) {
        if (statements == null || !statements.present()) {
            // Empty method body - check if we have return
            if (returnNode != null) {
                CFGNode retNode = cfg.createNode("return");
                retNode.setControlFlowNode(returnNode, "return");
                String returnVar = extractReturnVariable(returnNode);
                retNode.setReturnVariable(returnVar);
                cfg.connect(cfg.getEntry(), retNode);
                cfg.connect(retNode, cfg.getExit());
            } else {
                cfg.connect(cfg.getEntry(), cfg.getExit());
            }
            return;
        }
        
        List<Statement> stmtList = extractStatements(statements);
        
        // Build the statement chain
        CFGNode currentExitPoint = cfg.getEntry();
        for (int i = 0; i < stmtList.size(); i++) {
            Statement stmt = stmtList.get(i);
            StatementChain chain = buildStatementChain(stmt);
            
            if (chain != null) {
                cfg.connect(currentExitPoint, chain.getFirstNode());
                currentExitPoint = chain.getLastNode();
            }
        }
        
        // Connect to return node if exists, otherwise to exit
        if (returnNode != null) {
            CFGNode retNode = cfg.createNode("return");
            retNode.setControlFlowNode(returnNode, "return");
            String returnVar = extractReturnVariable(returnNode);
            retNode.setReturnVariable(returnVar);
            cfg.connect(currentExitPoint, retNode);
            cfg.connect(retNode, cfg.getExit());
        } else {
            cfg.connect(currentExitPoint, cfg.getExit());
        }
    }
    
    /**
     * Build CFG from a list of statements (without return - for backward compatibility)
     */
    public void buildCFG(NodeListOptional statements) {
        buildCFG(statements, null);
    }
    
    /**
     * Helper: Extract all statements from NodeListOptional
     */
    private List<Statement> extractStatements(NodeListOptional nodeList) {
        List<Statement> result = new ArrayList<>();
        if (!nodeList.present()) return result;
        
        for (Enumeration<Node> e = nodeList.elements(); e.hasMoreElements(); ) {
            Node node = e.nextElement();
            if (node instanceof Statement) {
                result.add((Statement) node);
            }
        }
        return result;
    }
    
    /**
     * Build CFG for a single statement
     * Returns a StatementChain with first and last nodes
     */
    private StatementChain buildStatementChain(Statement stmt) {
        // Check the statement type using f0 (the choice node)
        Node choice = stmt.f0.choice;
        
        if (choice instanceof Block) {
            return buildBlock((Block) choice);
        } else if (choice instanceof IfStatement) {
            StatementChain chain = buildIfStatement((IfStatement) choice);
            stmtToCFGNode.put(stmt, chain.getFirstNode());  // Map Statement to condition node
            return chain;
        } else if (choice instanceof WhileStatement) {
            StatementChain chain = buildWhileStatement((WhileStatement) choice);
            stmtToCFGNode.put(stmt, chain.getFirstNode());  // Map Statement to condition node
            return chain;
        } else if (choice instanceof ForStatement) {
            StatementChain chain = buildForStatement((ForStatement) choice);
            stmtToCFGNode.put(stmt, chain.getFirstNode());  // Map Statement to condition node
            return chain;
        } else {
            // Linear statement: Assignment, ArrayAssignment, FieldAssignment, Print, VoidMessageSend
            CFGNode node = cfg.createNode("stmt");
            node.addStatement(stmt);
            stmtToCFGNode.put(stmt, node);  // Map AST Statement to CFGNode
            return new StatementChain(node, node);
        }
    }
    
    /**
     * Build CFG for a Block: group all statements
     */
    private StatementChain buildBlock(Block block) {
        List<Statement> statements = extractStatements(block.f1);
        
        if (statements.isEmpty()) {
            // Empty block - create a dummy node
            CFGNode node = cfg.createNode("empty_block");
            return new StatementChain(node, node);
        }
        
        // Build first statement
        StatementChain chain = buildStatementChain(statements.get(0));
        
        // Chain the rest
        for (int i = 1; i < statements.size(); i++) {
            StatementChain nextChain = buildStatementChain(statements.get(i));
            cfg.connect(chain.getLastNode(), nextChain.getFirstNode());
            chain = new StatementChain(chain.getFirstNode(), nextChain.getLastNode());
        }
        
        return chain;
    }
    
    /**
     * Build CFG for if-statement with branching and MEET at merge point
     * if (cond) thenStmt else elseStmt
     * Creates: condition node -> then branch -> merge point
     *                         -> else branch -> merge point
     * At merge point: uses MEET operation
     * ⊥ ⊓ x = ⊥ (bottom)
     * c1 ⊓ c1 = c1 (same constant)
     * c1 ⊓ c2 = ⊥ (different constants - lose info)
     * T ⊓ x = x
     */
    private StatementChain buildIfStatement(IfStatement ifStmt) {
        // Create condition check node
        CFGNode condNode = cfg.createNode("if_cond");
        condNode.setControlFlowNode(ifStmt, "if");
        
        // Build then branch
        Statement thenStmt = ifStmt.f4;
        StatementChain thenChain = buildStatementChain(thenStmt);
        cfg.connect(condNode, thenChain.getFirstNode());
        
        // Build else branch
        Statement elseStmt = ifStmt.f6;
        StatementChain elseChain = buildStatementChain(elseStmt);
        cfg.connect(condNode, elseChain.getFirstNode());
        
        // Merge point - will apply MEET operation during data flow analysis
        CFGNode mergeNode = cfg.createNode("if_merge");
        cfg.connect(thenChain.getLastNode(), mergeNode);
        cfg.connect(elseChain.getLastNode(), mergeNode);
        
        return new StatementChain(condNode, mergeNode);
    }
    
    /**
     * Build CFG for while-statement with loop back and MEET at merge
     * while (cond) body
     * Creates: condition check -> body -> loop back to condition
     *          condition ---------> exit (when false)
     * Loop back uses MEET: variables from loop body ⊓ variables before loop
     */
    private StatementChain buildWhileStatement(WhileStatement whileStmt) {
        // Create condition check node
        CFGNode condNode = cfg.createNode("while_cond");
        condNode.setControlFlowNode(whileStmt, "while");
        
        // Build loop body
        Statement body = whileStmt.f4;
        StatementChain bodyChain = buildStatementChain(body);
        cfg.connect(condNode, bodyChain.getFirstNode());
        
        // Loop back: connect body end to condition
        cfg.connect(bodyChain.getLastNode(), condNode);
        
        // Exit node (when condition is false)
        CFGNode exitNode = cfg.createNode("while_exit");
        // The condNode has two successors: one for loop body, one for exit
        cfg.connect(condNode, exitNode);
        
        return new StatementChain(condNode, exitNode);
    }
    
    /**
     * Build CFG for for-statement with MEET at merge points
     * for (init; cond; increment) body
     * Creates: init -> condition check -> body -> increment -> loop back to condition
     *          condition ---------> exit
     * Loop back uses MEET semantics for variable state convergence
     */
    private StatementChain buildForStatement(ForStatement forStmt) {
        // Note: init, cond, increment are Expressions in TACoJava
        // We'll create nodes to represent these
        
        // Create init node
        CFGNode initNode = cfg.createNode("for_init");
        // In real implementation, we'd evaluate forStmt.f4 (init expression)
        
        // Create condition check node
        CFGNode condNode = cfg.createNode("for_cond");
        condNode.setControlFlowNode(forStmt, "for");
        cfg.connect(initNode, condNode);
        
        // Build loop body
        Statement body = forStmt.f12;
        StatementChain bodyChain = buildStatementChain(body);
        cfg.connect(condNode, bodyChain.getFirstNode());
        
        // Create increment node
        CFGNode incrNode = cfg.createNode("for_incr");
        cfg.connect(bodyChain.getLastNode(), incrNode);
        
        // Loop back to condition
        cfg.connect(incrNode, condNode);
        
        // Exit node
        CFGNode exitNode = cfg.createNode("for_exit");
        cfg.connect(condNode, exitNode);
        
        return new StatementChain(initNode, exitNode);
    }
    
    /**
     * Extract the return variable name from a ConstOrId node
     * ConstOrId can be: IntegerLiteral, Identifier, TrueLiteral, FalseLiteral
     */
    private String extractReturnVariable(Node constOrIdNode) {
        if (constOrIdNode == null) return null;
        
        // ConstOrId.f0 is the choice
        if (constOrIdNode instanceof syntaxtree.ConstOrId) {
            syntaxtree.ConstOrId constOrId = (syntaxtree.ConstOrId) constOrIdNode;
            Node choice = constOrId.f0.choice;
            
            // Check if it's an Identifier
            if (choice instanceof syntaxtree.Identifier) {
                syntaxtree.Identifier id = (syntaxtree.Identifier) choice;
                return id.f0.tokenImage;
            }
            // For literals (IntegerLiteral, TrueLiteral, FalseLiteral), return their string representation
            else if (choice instanceof syntaxtree.IntegerLiteral) {
                return "<literal>";
            }
            else if (choice instanceof syntaxtree.TrueLiteral) {
                return "true";
            }
            else if (choice instanceof syntaxtree.FalseLiteral) {
                return "false";
            }
        }
        return "<unknown>";
    }
    
    /**
     * Helper class to represent a chain of statements in CFG
     * firstNode: entry point, lastNode: exit point
     */
    private static class StatementChain {
        private CFGNode firstNode;
        private CFGNode lastNode;
        
        StatementChain(CFGNode first, CFGNode last) {
            this.firstNode = first;
            this.lastNode = last;
        }
        
        CFGNode getFirstNode() {
            return firstNode;
        }
        
        CFGNode getLastNode() {
            return lastNode;
        }
    }
}
