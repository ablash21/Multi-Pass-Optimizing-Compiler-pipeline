package visitor;
import syntaxtree.*;
import java.util.*;

/**
 * Provides default methods which visit each node in the tree in depth-first
 * order.  Your visitors may extend this class.
 */
public class pass5<R,A> implements GJVisitor<R,A> {
   
   // Pass 5 Code Generation Fields
   private StringBuilder output;
   private Pass5ExpressionEvaluator exprEval;
   private PredicateEvaluator predEval;
   private DeadCodeDetector deadCodeDetector;
   
   private String currClass = "";
   private String currMethod = "";
   private CFG currCFG = null;
   private Map<String, CFG> methodCFGs;
   private int loopDepth = 0;
   private int conditionalDepth = 0;
   private Set<String> currentMethodParams = new HashSet<>();
   
   // Constructor
   public pass5(Map<String, CFG> methodCFGs) {
      this.methodCFGs = methodCFGs != null ? methodCFGs : new HashMap<>();
      this.output = new StringBuilder();
      this.exprEval = new Pass5ExpressionEvaluator();
      this.predEval = new PredicateEvaluator();
      this.deadCodeDetector = new DeadCodeDetector();
   }
   
   // Get final optimized code
   public String getOutput() {
      return output.toString();
   }
   
   //
   // Auto class visitors--probably don't need to be overridden.
   //
   public R visit(NodeList n, A argu) {
      R _ret=null;
      int _count=0;
      for ( Enumeration<Node> e = n.elements(); e.hasMoreElements(); ) {
         e.nextElement().accept(this,argu);
         _count++;
      }
      return _ret;
   }

   public R visit(NodeListOptional n, A argu) {
      if ( n.present() ) {
         R _ret=null;
         int _count=0;
         for ( Enumeration<Node> e = n.elements(); e.hasMoreElements(); ) {
            e.nextElement().accept(this,argu);
            _count++;
         }
         return _ret;
      }
      else
         return null;
   }

   public R visit(NodeOptional n, A argu) {
      if ( n.present() )
         return n.node.accept(this,argu);
      else
         return null;
   }

   public R visit(NodeSequence n, A argu) {
      R _ret=null;
      int _count=0;
      for ( Enumeration<Node> e = n.elements(); e.hasMoreElements(); ) {
         e.nextElement().accept(this,argu);
         _count++;
      }
      return _ret;
   }

   public R visit(NodeToken n, A argu) { return null; }

   //
   // User-generated visitor methods below
   //

   /**
    * f0 -> MainClass()
    * f1 -> ( TypeDeclaration() )*
    * f2 -> <EOF>
    */
   public R visit(Goal n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "class"
    * f1 -> Identifier()
    * f2 -> "{"
    * f3 -> "public"
    * f4 -> "static"
    * f5 -> "void"
    * f6 -> "main"
    * f7 -> "("
    * f8 -> "String"
    * f9 -> "["
    * f10 -> "]"
    * f11 -> Identifier()
    * f12 -> ")"
    * f13 -> "{"
    * f14 -> ( VarDeclaration() )*
    * f15 -> ( Statement() )*
    * f16 -> "}"
    * f17 -> "}"
    */
   public R visit(MainClass n, A argu) {
      // Generate: class ClassName {
      String className = n.f1.f0.tokenImage;
      output.append("class ").append(className).append(" {\n");
      output.append("    public static void main(String[] args) {\n");
      
      // Set currCFG for statement processing
      currCFG = methodCFGs.get(className + ".main");
      
      // Phase 5: Handle variable declarations
      if (n.f14.present()) {
         Enumeration<Node> varNodes = n.f14.elements();
         while (varNodes.hasMoreElements()) {
            VarDeclaration v = (VarDeclaration) varNodes.nextElement();
            output.append("        ").append(getTypeString(v.f0))
                  .append(" ").append(v.f1.f0.tokenImage).append(";\n");
         }
      }
      
      // Phase 5 & 6: Handle statements (constant propagation + dead code elimination)
      if (n.f15.present()) {
         Enumeration<Node> stmtNodes = n.f15.elements();
         while (stmtNodes.hasMoreElements()) {
            Statement s = (Statement) stmtNodes.nextElement();
            visitStatement(s, className + ".main");
         }
      }
      
      output.append("    }\n");
      output.append("}\n\n");
      
      // Reset currCFG
      currCFG = null;
      
      return null;
   }

   /**
    * f0 -> ClassDeclaration()
    *       | ClassExtendsDeclaration()
    */
   public R visit(TypeDeclaration n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "class"
    * f1 -> Identifier()
    * f2 -> "{"
    * f3 -> ( VarDeclaration() )*
    * f4 -> ( MethodDeclaration() )*
    * f5 -> "}"
    */
   public R visit(ClassDeclaration n, A argu) {
      // Generate: class ClassName {
      String className = n.f1.f0.tokenImage;
      currClass = className;
      output.append("class ").append(className).append(" {\n");
      
      // Generate field declarations
      if (n.f3.present()) {
         Enumeration<Node> fields = n.f3.elements();
         while (fields.hasMoreElements()) {
            VarDeclaration v = (VarDeclaration) fields.nextElement();
            output.append("    ").append(getTypeString(v.f0))
                  .append(" ").append(v.f1.f0.tokenImage).append(";\n");
         }
      }
      
      // Generate method declarations
      if (n.f4.present()) {
         Enumeration<Node> methods = n.f4.elements();
         while (methods.hasMoreElements()) {
            MethodDeclaration m = (MethodDeclaration) methods.nextElement();
            visitMethodDeclaration(m, className);
         }
      }
      
      output.append("}\n\n");
      currClass = "";
      return null;
   }

   /**
    * f0 -> "class"
    * f1 -> Identifier()
    * f2 -> "extends"
    * f3 -> Identifier()
    * f4 -> "{"
    * f5 -> ( VarDeclaration() )*
    * f6 -> ( MethodDeclaration() )*
    * f7 -> "}"
    */
   public R visit(ClassExtendsDeclaration n, A argu) {
      // Generate: class ClassName extends ParentClass {
      String className = n.f1.f0.tokenImage;
      String parentClass = n.f3.f0.tokenImage;
      currClass = className;
      output.append("class ").append(className).append(" extends ")
            .append(parentClass).append(" {\n");
      
      // Generate field declarations
      if (n.f5.present()) {
         Enumeration<Node> fields = n.f5.elements();
         while (fields.hasMoreElements()) {
            VarDeclaration v = (VarDeclaration) fields.nextElement();
            output.append("    ").append(getTypeString(v.f0))
                  .append(" ").append(v.f1.f0.tokenImage).append(";\n");
         }
      }
      
      // Generate method declarations
      if (n.f6.present()) {
         Enumeration<Node> methods = n.f6.elements();
         while (methods.hasMoreElements()) {
            MethodDeclaration m = (MethodDeclaration) methods.nextElement();
            visitMethodDeclaration(m, className);
         }
      }
      
      output.append("}\n\n");
      currClass = "";
      return null;
   }

   /**
    * f0 -> Type()
    * f1 -> Identifier()
    * f2 -> ";"
    */
   public R visit(VarDeclaration n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "public"
    * f1 -> Type()
    * f2 -> Identifier()
    * f3 -> "("
    * f4 -> ( FormalParameterList() )?
    * f5 -> ")"
    * f6 -> "{"
    * f7 -> ( VarDeclaration() )*
    * f8 -> ( Statement() )*
    * f9 -> "return"
    * f10 -> ConstOrId()
    * f11 -> ";"
    * f12 -> "}"
    */
   public R visit(MethodDeclaration n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      n.f5.accept(this, argu);
      n.f6.accept(this, argu);
      n.f7.accept(this, argu);
      n.f8.accept(this, argu);
      n.f9.accept(this, argu);
      n.f10.accept(this, argu);
      n.f11.accept(this, argu);
      n.f12.accept(this, argu);
      return _ret;
   }
   
   /**
    * Generate method body with optimized code
    */
   private void visitMethodDeclaration(MethodDeclaration m, String className) {
      // Extract method signature
      String returnType = getTypeString(m.f1);
      String methodName = m.f2.f0.tokenImage;
      String methodSig = className + "." + methodName;
      
      // SKIP unreachable methods (not called from call graph)
      if (!SymbolTable.reachableMethods.contains(methodSig)) {
         return;
      }
      
      // Set currCFG for this method
      currCFG = methodCFGs.get(methodSig);
      
      // Generate method signature
      output.append("    public ").append(returnType).append(" ")
            .append(methodName).append("(");
      
      // Generate parameters
      List<String> paramNames = new ArrayList<>();
      if (m.f4.present()) {
         FormalParameterList paramList = (FormalParameterList) m.f4.node;
         // f0 -> FormalParameter
         FormalParameter firstParam = paramList.f0;
         output.append(getTypeString(firstParam.f0)).append(" ")
               .append(firstParam.f1.f0.tokenImage);
         paramNames.add(firstParam.f1.f0.tokenImage);
         
         // f1 -> (FormalParameterRest)*
         if (paramList.f1.present()) {
            Enumeration<Node> rests = paramList.f1.elements();
            while (rests.hasMoreElements()) {
               FormalParameterRest rest = (FormalParameterRest) rests.nextElement();
               FormalParameter param = rest.f1;
               output.append(", ").append(getTypeString(param.f0)).append(" ")
                     .append(param.f1.f0.tokenImage);
               paramNames.add(param.f1.f0.tokenImage);
            }
         }
      }
      currentMethodParams = new HashSet<>(paramNames);
      output.append(") {\n");
      
      // Generate local variable declarations
      if (m.f7.present()) {
         Enumeration<Node> vars = m.f7.elements();
         while (vars.hasMoreElements()) {
            VarDeclaration v = (VarDeclaration) vars.nextElement();
            output.append("        ").append(getTypeString(v.f0))
                  .append(" ").append(v.f1.f0.tokenImage).append(";\n");
         }
      }
      
      // Generate method body statements
      if (m.f8.present()) {
         Enumeration<Node> stmts = m.f8.elements();
         while (stmts.hasMoreElements()) {
            Statement s = (Statement) stmts.nextElement();
            visitStatement(s, methodSig);
         }
      }
      
      // Conservative return folding: only fold when all method return nodes agree on one constant.
      String returnExpr = conservativeReturnExpr(m.f10);
      output.append("        return ").append(returnExpr).append(";\n");
      
      output.append("    }\n\n");
      
      // Reset currCFG
      currCFG = null;
      currentMethodParams = new HashSet<>();
   }

   /**
    * f0 -> FormalParameter()
    * f1 -> ( FormalParameterRest() )*
    */
   public R visit(FormalParameterList n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> Type()
    * f1 -> Identifier()
    */
   public R visit(FormalParameter n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> ","
    * f1 -> FormalParameter()
    */
   public R visit(FormalParameterRest n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> ArrayType()
    *       | BooleanType()
    *       | IntegerType()
    *       | Identifier()
    */
   public R visit(Type n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "int"
    * f1 -> "["
    * f2 -> "]"
    */
   public R visit(ArrayType n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "boolean"
    */
   public R visit(BooleanType n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "int"
    */
   public R visit(IntegerType n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> Block()
    *       | AssignmentStatement()
    *       | ArrayAssignmentStatement()
    *       | FieldAssignmentStatement()
    *       | VoidMessageSendStatement()
    *       | IfStatement()
    *       | WhileStatement()
    *       | ForStatement()
    *       | PrintStatement()
    */
   public R visit(Statement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "{"
    * f1 -> ( Statement() )*
    * f2 -> "}"
    */
   public R visit(Block n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> Identifier()
    * f1 -> "="
    * f2 -> Expression()
    * f3 -> ";"
    */
   public R visit(AssignmentStatement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> MessageSend()
    * f1 -> ";"
    */
   public R visit(VoidMessageSendStatement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> Identifier()
    * f1 -> "["
    * f2 -> ConstOrId()
    * f3 -> "]"
    * f4 -> "="
    * f5 -> ConstOrId()
    * f6 -> ";"
    */
   public R visit(ArrayAssignmentStatement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      n.f5.accept(this, argu);
      n.f6.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> Identifier()
    * f1 -> "."
    * f2 -> Identifier()
    * f3 -> "="
    * f4 -> ConstOrId()
    * f5 -> ";"
    */
   public R visit(FieldAssignmentStatement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      n.f5.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "if"
    * f1 -> "("
    * f2 -> Identifier()
    * f3 -> ")"
    * f4 -> Statement()
    * f5 -> "else"
    * f6 -> Statement()
    */
   public R visit(IfStatement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      n.f5.accept(this, argu);
      n.f6.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "while"
    * f1 -> "("
    * f2 -> Identifier()
    * f3 -> ")"
    * f4 -> Statement()
    */
   public R visit(WhileStatement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "for"
    * f1 -> "("
    * f2 -> Identifier()
    * f3 -> "="
    * f4 -> Expression()
    * f5 -> ";"
    * f6 -> Expression()
    * f7 -> ";"
    * f8 -> Identifier()
    * f9 -> "="
    * f10 -> Expression()
    * f11 -> ")"
    * f12 -> Statement()
    */
   public R visit(ForStatement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      n.f5.accept(this, argu);
      n.f6.accept(this, argu);
      n.f7.accept(this, argu);
      n.f8.accept(this, argu);
      n.f9.accept(this, argu);
      n.f10.accept(this, argu);
      n.f11.accept(this, argu);
      n.f12.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "System.out.println"
    * f1 -> "("
    * f2 -> ConstOrId()
    * f3 -> ")"
    * f4 -> ";"
    */
   public R visit(PrintStatement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> CompareExpression()
    *       | PlusExpression()
    *       | MinusExpression()
    *       | TimesExpression()
    *       | ArrayLookup()
    *       | ArrayLength()
    *       | MessageSend()
    *       | FieldRead()
    *       | PrimaryExpression()
    */
   public R visit(Expression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> ConstOrId()
    * f1 -> "<"
    * f2 -> ConstOrId()
    */
   public R visit(CompareExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> ConstOrId()
    * f1 -> "+"
    * f2 -> ConstOrId()
    */
   public R visit(PlusExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> ConstOrId()
    * f1 -> "-"
    * f2 -> ConstOrId()
    */
   public R visit(MinusExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> ConstOrId()
    * f1 -> "*"
    * f2 -> ConstOrId()
    */
   public R visit(TimesExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> Identifier()
    * f1 -> "["
    * f2 -> ConstOrId()
    * f3 -> "]"
    */
   public R visit(ArrayLookup n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> Identifier()
    * f1 -> "."
    * f2 -> "length"
    */
   public R visit(ArrayLength n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> Identifier()
    * f1 -> "."
    * f2 -> Identifier()
    */
   public R visit(FieldRead n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> PrimaryExpression()
    * f1 -> "."
    * f2 -> Identifier()
    * f3 -> "("
    * f4 -> ( ArgList() )?
    * f5 -> ")"
    */
   public R visit(MessageSend n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      n.f5.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> ConstOrId()
    * f1 -> ( ArgRest() )*
    */
   public R visit(ArgList n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> ","
    * f1 -> ConstOrId()
    */
   public R visit(ArgRest n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> IntegerLiteral()
    *       | TrueLiteral()
    *       | FalseLiteral()
    *       | Identifier()
    *       | ThisExpression()
    *       | ArrayAllocationExpression()
    *       | AllocationExpression()
    *       | NotExpression()
    */
   public R visit(PrimaryExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> IntegerLiteralWithPosSign()
    *       | IntegerLiteralWithNegSign()
    *       | PlainIntegerLiteral()
    */
   public R visit(IntegerLiteral n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> <INTEGER_LITERAL>
    */
   public R visit(PlainIntegerLiteral n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "+"
    * f1 -> <INTEGER_LITERAL>
    */
   public R visit(IntegerLiteralWithPosSign n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "-"
    * f1 -> <INTEGER_LITERAL>
    */
   public R visit(IntegerLiteralWithNegSign n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> IntegerLiteral()
    *       | Identifier()
    *       | TrueLiteral()
    *       | FalseLiteral()
    */
   public R visit(ConstOrId n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "true"
    */
   public R visit(TrueLiteral n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "false"
    */
   public R visit(FalseLiteral n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> <IDENTIFIER>
    */
   public R visit(Identifier n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "this"
    */
   public R visit(ThisExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "new"
    * f1 -> "int"
    * f2 -> "["
    * f3 -> ConstOrId()
    * f4 -> "]"
    */
   public R visit(ArrayAllocationExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "new"
    * f1 -> Identifier()
    * f2 -> "("
    * f3 -> ")"
    */
   public R visit(AllocationExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "!"
    * f1 -> Identifier()
    */
   public R visit(NotExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   // Helper Methods for Code Generation

   /**
    * Convert Type AST node to string representation ("int", "boolean", "int[]", or class name)
    */
   private String getTypeString(Type t) {
      if (t == null || t.f0 == null) return "Object";
      
      Object typeChoice = t.f0.choice;
      if (typeChoice instanceof IntegerType) {
         return "int";
      } else if (typeChoice instanceof BooleanType) {
         return "boolean";
      } else if (typeChoice instanceof ArrayType) {
         return "int[]";
      } else if (typeChoice instanceof Identifier) {
         // Class type
         Identifier classId = (Identifier) typeChoice;
         return classId.f0.tokenImage;
      }
      
      return "Object";
   }

   /**
    * Handle a Statement and generate optimized code
    * Handles dead code elimination and constant propagation
    */
   private void visitStatement(Statement stmt, String methodSig) {
      if (stmt == null || stmt.f0 == null) return;
      
      // Extract class and method from methodSig if needed
      if (methodSig != null && !methodSig.isEmpty()) {
         int dotIndex = methodSig.lastIndexOf('.');
         if (dotIndex > 0) {
            currClass = methodSig.substring(0, dotIndex);
            currMethod = methodSig.substring(dotIndex + 1);
         }
      }
      
      Object choice = stmt.f0.choice;
      
      // Get CFG node for this statement from the method's CFG
      CFGNode cfgNode = null;
      if (currCFG != null && currCFG.getStatementToCFGNodeMap() != null) {
         cfgNode = currCFG.getStatementToCFGNodeMap().get(stmt);
      }
      
      // Do not skip CFG-marked unreachable statements in pass5 codegen.
      // The reachability marks are not always path-precise for recursive/loop-heavy methods.
      
      // Use IN state for constants (dataflow before processing this statement).
      Map<String, LatticeValue> constants = new HashMap<>();
      if (cfgNode != null && cfgNode.in != null) {
         constants.putAll(cfgNode.in);
      }
      // Do not substitute formal parameters; context-insensitive summaries are unsafe for recursive/branchy methods.
      for (String param : currentMethodParams) {
         constants.remove(param);
      }
      // Inside loops, disable constant substitution to avoid stale header facts.
      Map<String, LatticeValue> effectiveConstants = loopDepth > 0 ? new HashMap<>() : constants;
      
      try {
         if (choice instanceof Block) {
            Block block = (Block) choice;
            if (block.f1.present()) {
               Enumeration<Node> stmts = block.f1.elements();
               while (stmts.hasMoreElements()) {
                  visitStatement((Statement) stmts.nextElement(), methodSig);
               }
            }
         
         } else if (choice instanceof AssignmentStatement) {
            AssignmentStatement assign = (AssignmentStatement) choice;
            
            String varName = assign.f0.f0.tokenImage;
            
            // System.out.println("[DCE-START] " + varName + " | cfgNode_before_check=" + (cfgNode != null ? cfgNode.getLabel() : "null"));
            
            // Check if this variable is live after this statement (using liveness analysis)
            boolean isLive = isVariableLive(varName, cfgNode);
            boolean hasSideEffects = expressionHasSideEffects(assign.f2);
            boolean controlsFlowHeader = isControlFlowHeaderVariable(varName);
            
            // Debug output
            // System.out.println("[DCE-END] " + varName + " = ... | isLive=" + isLive + " | hasSideEffects=" + hasSideEffects + " | cfgNode=" + (cfgNode != null ? cfgNode.getLabel() : "null"));
            
            // Keep assignment if:
            // 1. Variable is live (used after), OR
            // 2. Expression has side effects (method call with side effects)
            if (loopDepth > 0 || conditionalDepth > 0) {
               EvalResult rhs = exprEval.evaluate(assign.f2, effectiveConstants);
               output.append("        ").append(varName).append(" = ")
                     .append(rhs.optimizedExpr).append(";\n");
            } else if (isLive || controlsFlowHeader) {
               EvalResult rhs = exprEval.evaluate(assign.f2, effectiveConstants);
               output.append("        ").append(varName).append(" = ")
                     .append(rhs.optimizedExpr).append(";\n");
            } else {
               MessageSend deadCall = extractMessageSend(assign.f2);
               if (deadCall != null) {
                  String callStr = messageSendToString(deadCall, effectiveConstants);
                  output.append("        ").append(callStr).append(";\n");
               } else if (hasSideEffects) {
                  EvalResult rhs = exprEval.evaluate(assign.f2, effectiveConstants);
                  output.append("        ").append(varName).append(" = ")
                        .append(rhs.optimizedExpr).append(";\n");
               }
            }
            // Otherwise, skip the dead assignment (variable not live and no side effects)
         
         } else if (choice instanceof FieldAssignmentStatement) {
            FieldAssignmentStatement fieldAssign = (FieldAssignmentStatement) choice;
            
            String objName = fieldAssign.f0.f0.tokenImage;
            String fieldName = fieldAssign.f2.f0.tokenImage;
            String rhsStr = constOrIdToString(fieldAssign.f4, effectiveConstants);
            
            output.append("        ").append(objName).append(".").append(fieldName)
                  .append(" = ").append(rhsStr).append(";\n");
         
         } else if (choice instanceof ArrayAssignmentStatement) {
            ArrayAssignmentStatement arrayAssign = (ArrayAssignmentStatement) choice;
            
            String arrName = arrayAssign.f0.f0.tokenImage;
            String indexStr = constOrIdToString(arrayAssign.f2, effectiveConstants);
            String rhsStr = constOrIdToString(arrayAssign.f5, effectiveConstants);
            
            output.append("        ").append(arrName).append("[").append(indexStr)
                  .append("] = ").append(rhsStr).append(";\n");
         
         } else if (choice instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement) choice;
            
            String condName = ifStmt.f2.f0.tokenImage;
            LatticeValue condVal = effectiveConstants.get(condName);
            boolean foldConstIf = loopDepth == 0
                  && conditionalDepth == 0
                  && condVal != null
                  && condVal.isConstant()
                  && condVal.getType() == LatticeValue.Type.BOOLEAN;

            if (foldConstIf) {
               if (condVal.getBoolValue()) {
                  visitStatement(ifStmt.f4, methodSig);
               } else {
                  visitStatement(ifStmt.f6, methodSig);
               }
            } else {
               output.append("        if (").append(condName).append(") {\n");
               conditionalDepth++;
               try {
                  visitStatement(ifStmt.f4, methodSig);
               } finally {
                  conditionalDepth--;
               }
               output.append("        } else {\n");
               conditionalDepth++;
               try {
                  visitStatement(ifStmt.f6, methodSig);
               } finally {
                  conditionalDepth--;
               }
               output.append("        }\n");
            }
         
         } else if (choice instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement) choice;

            String condName = whileStmt.f2.f0.tokenImage;
            LatticeValue whileCond = effectiveConstants.get(condName);
            boolean pruneWhile = loopDepth == 0
                  && conditionalDepth == 0
                  && whileCond != null
                  && whileCond.isConstant()
                  && whileCond.getType() == LatticeValue.Type.BOOLEAN
                  && !whileCond.getBoolValue();

            if (!pruneWhile) {
               output.append("        while (").append(condName).append(") {\n");
               loopDepth++;
               conditionalDepth++;
               try {
                  visitStatement(whileStmt.f4, methodSig);
               } finally {
                  conditionalDepth--;
                  loopDepth--;
               }
               output.append("        }\n");
            }

         } else if (choice instanceof ForStatement) {
            ForStatement forStmt = (ForStatement) choice;

            String initVar = forStmt.f2.f0.tokenImage;
            String updateVar = forStmt.f8.f0.tokenImage;
            // Keep for-loop form and avoid folding header expressions.
            Map<String, LatticeValue> headerConstants = new HashMap<>();

            String initExpr = expressionToString(forStmt.f4, headerConstants);
            String condExpr = expressionToString(forStmt.f6, headerConstants);
            String updateExpr = expressionToString(forStmt.f10, headerConstants);

            EvalResult condEval = exprEval.evaluate(forStmt.f6, effectiveConstants);
            boolean pruneFor = loopDepth == 0
                  && conditionalDepth == 0
                  && condEval != null
                  && condEval.isConstant
                  && (condEval.constantValue instanceof Boolean)
                  && !((Boolean) condEval.constantValue);

            if (pruneFor) {
               // Preserve entry-side effects of for-init when loop body is proven unreachable.
               output.append("        ").append(initVar).append(" = ").append(initExpr).append(";\n");
            } else {
               output.append("        for (")
                     .append(initVar).append(" = ").append(initExpr).append("; ")
                     .append(condExpr).append("; ")
                     .append(updateVar).append(" = ").append(updateExpr).append(") {\n");
               loopDepth++;
               conditionalDepth++;
               try {
                  visitStatement(forStmt.f12, methodSig);
               } finally {
                  conditionalDepth--;
                  loopDepth--;
               }
               output.append("        }\n");
            }
         
         } else if (choice instanceof PrintStatement) {
            PrintStatement printStmt = (PrintStatement) choice;
            
            String exprStr = constOrIdToString(printStmt.f2, effectiveConstants);
            output.append("        System.out.println(").append(exprStr).append(");\n");
         
         } else if (choice instanceof VoidMessageSendStatement) {
            VoidMessageSendStatement voidMsg = (VoidMessageSendStatement) choice;
            
            // Output function call (dead code detection disabled)
            MessageSend msgSend = voidMsg.f0;
            String callStr = messageSendToString(msgSend, effectiveConstants);
            output.append("        ").append(callStr).append(";\n");
         }
      } catch (Exception e) {
         output.append("        // Error processing statement\n");
      }
   }

   /**
    * Convert ConstOrId to its string representation
    * ConstOrId is either: IntegerLiteral, Identifier, TrueLiteral, or FalseLiteral
    */
   private String constOrIdToString(ConstOrId c, Map<String, LatticeValue> constants) {
      if (c == null || c.f0 == null) return "0";
      
      Object choice = c.f0.choice;
      
      if (choice instanceof IntegerLiteral) {
         // IntegerLiteral -> choice is PlainIntegerLiteral or with sign
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
         // Variable reference - check constants
         Identifier id = (Identifier) choice;
         String varName = id.f0.tokenImage;
         LatticeValue lv = constants.get(varName);
         if (lv != null && lv.isConstant()) {
            if (lv.getType() == LatticeValue.Type.INT) {
               return String.valueOf(lv.getIntValue());
            }
            if (lv.getType() == LatticeValue.Type.BOOLEAN) {
               return String.valueOf(lv.getBoolValue());
            }
         }
         return varName;
         
      } else if (choice instanceof TrueLiteral) {
         return "true";
         
      } else if (choice instanceof FalseLiteral) {
         return "false";
      }
      
      return "0";
   }

   /**
    * Extract and format MessageSend for code generation
    * MessageSend: objExpr.methodName(args)
    */
   private String messageSendToString(MessageSend msg, Map<String, LatticeValue> constants) {
      if (msg == null) return "";
      
      StringBuilder sb = new StringBuilder();
      
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
      
      // f1: "."
      // f2: Identifier (method name)
      sb.append(".").append(msg.f2.f0.tokenImage);
      
      // f3: "("
      // f4: NodeOptional (arguments)
      // f5: ")"
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
      return sb.toString();
   }

   /**
    * Convert Expression to Java code string with conservative constant substitution.
    */
   private String expressionToString(Expression expr, Map<String, LatticeValue> constants) {
      if (expr == null || expr.f0 == null) {
         return "0";
      }

      Object choice = expr.f0.choice;

      if (choice instanceof PrimaryExpression) {
         return primaryExpressionToString((PrimaryExpression) choice, constants);
      } else if (choice instanceof CompareExpression) {
         CompareExpression cmp = (CompareExpression) choice;
         return constOrIdToString(cmp.f0, constants) + " < " + constOrIdToString(cmp.f2, constants);
      } else if (choice instanceof PlusExpression) {
         PlusExpression plus = (PlusExpression) choice;
         return constOrIdToString(plus.f0, constants) + " + " + constOrIdToString(plus.f2, constants);
      } else if (choice instanceof MinusExpression) {
         MinusExpression minus = (MinusExpression) choice;
         return constOrIdToString(minus.f0, constants) + " - " + constOrIdToString(minus.f2, constants);
      } else if (choice instanceof TimesExpression) {
         TimesExpression times = (TimesExpression) choice;
         return constOrIdToString(times.f0, constants) + " * " + constOrIdToString(times.f2, constants);
      } else if (choice instanceof ArrayLookup) {
         ArrayLookup lookup = (ArrayLookup) choice;
         return lookup.f0.f0.tokenImage + "[" + constOrIdToString(lookup.f2, constants) + "]";
      } else if (choice instanceof ArrayLength) {
         ArrayLength len = (ArrayLength) choice;
         return len.f0.f0.tokenImage + ".length";
      } else if (choice instanceof MessageSend) {
         return messageSendToString((MessageSend) choice, constants);
      } else if (choice instanceof FieldRead) {
         FieldRead fieldRead = (FieldRead) choice;
         return fieldRead.f0.f0.tokenImage + "." + fieldRead.f2.f0.tokenImage;
      }

      return "0";
   }

   /**
    * Convert PrimaryExpression to Java code string.
    */
   private String primaryExpressionToString(PrimaryExpression prim, Map<String, LatticeValue> constants) {
      if (prim == null || prim.f0 == null) {
         return "0";
      }

      Object choice = prim.f0.choice;

      if (choice instanceof IntegerLiteral) {
         IntegerLiteral intLit = (IntegerLiteral) choice;
         Object intChoice = intLit.f0.choice;
         if (intChoice instanceof PlainIntegerLiteral) {
            return ((PlainIntegerLiteral) intChoice).f0.tokenImage;
         } else if (intChoice instanceof IntegerLiteralWithPosSign) {
            return ((IntegerLiteralWithPosSign) intChoice).f1.tokenImage;
         } else if (intChoice instanceof IntegerLiteralWithNegSign) {
            return "-" + ((IntegerLiteralWithNegSign) intChoice).f1.tokenImage;
         }
      } else if (choice instanceof Identifier) {
         String name = ((Identifier) choice).f0.tokenImage;
         LatticeValue lv = constants.get(name);
         if (lv != null && lv.isConstant()) {
            if (lv.getType() == LatticeValue.Type.INT) {
               return String.valueOf(lv.getIntValue());
            }
            if (lv.getType() == LatticeValue.Type.BOOLEAN) {
               return String.valueOf(lv.getBoolValue());
            }
         }
         return name;
      } else if (choice instanceof TrueLiteral) {
         return "true";
      } else if (choice instanceof FalseLiteral) {
         return "false";
      } else if (choice instanceof ThisExpression) {
         return "this";
      } else if (choice instanceof AllocationExpression) {
         AllocationExpression alloc = (AllocationExpression) choice;
         return "new " + alloc.f1.f0.tokenImage + "()";
      } else if (choice instanceof ArrayAllocationExpression) {
         ArrayAllocationExpression arrAlloc = (ArrayAllocationExpression) choice;
         return "new int[" + constOrIdToString(arrAlloc.f3, constants) + "]";
      } else if (choice instanceof NotExpression) {
         NotExpression notExpr = (NotExpression) choice;
         return "!" + notExpr.f1.f0.tokenImage;
      }

      return "0";
   }
   
   /**
    * Check if an expression contains a method call with side effects
    * Used to determine if a dead assignment should be kept due to side effects
    */
   private boolean expressionHasSideEffects(Expression expr) {
      if (expr == null || expr.f0 == null) {
         return false;
      }
      
      Object exprChoice = expr.f0.choice;
      
      // Check if expression is a MessageSend (method call)
      if (exprChoice instanceof MessageSend) {
         // Keep method-call RHS conservatively; downstream summaries can be imprecise.
         return true;
      }

      // Allocations must be preserved (assignment target may be dead, but allocation is effectful in this optimizer)
      if (exprChoice instanceof PrimaryExpression) {
         PrimaryExpression primary = (PrimaryExpression) exprChoice;
         if (primary.f0 != null) {
            Object primaryChoice = primary.f0.choice;
            if (primaryChoice instanceof AllocationExpression || primaryChoice instanceof ArrayAllocationExpression) {
               return true;
            }
         }
      }
      
      // Check recursively in sub-expressions (binary operators, etc.)
      if (exprChoice instanceof PlusExpression) {
         PlusExpression plus = (PlusExpression) exprChoice;
         // Left and right are ConstOrId, not Expression, so no recursion needed
         return false;
      }
      
      if (exprChoice instanceof MinusExpression) {
         return false;
      }
      
      if (exprChoice instanceof TimesExpression) {
         return false;
      }
      
      if (exprChoice instanceof CompareExpression) {
         return false;
      }
      
      // Other expression types (PrimaryExpression, ArrayLookup, ArrayLength, FieldRead)
      // don't contain MessageSend in their basic structure
      return false;
   }

   /**
    * Extract direct message send from RHS expression when present.
    */
   private MessageSend extractMessageSend(Expression expr) {
      if (expr == null || expr.f0 == null) {
         return null;
      }
      Object exprChoice = expr.f0.choice;
      if (exprChoice instanceof MessageSend) {
         return (MessageSend) exprChoice;
      }
      return null;
   }

   /**
    * Fold return identifier to a literal only when every CFG return node agrees.
    */
   private String conservativeReturnExpr(ConstOrId retExpr) {
      String fallback = constOrIdToString(retExpr, new HashMap<>());
      if (retExpr == null || retExpr.f0 == null) {
         return fallback;
      }

      Object choice = retExpr.f0.choice;
      if (!(choice instanceof Identifier)) {
         return fallback;
      }

      String varName = ((Identifier) choice).f0.tokenImage;
      if (currentMethodParams.contains(varName) || currCFG == null) {
         return fallback;
      }

      LatticeValue agreed = null;
      boolean sawReturnNode = false;

      for (CFGNode node : currCFG.getNodes()) {
         if (!varName.equals(node.getReturnVariable())) {
            continue;
         }
         sawReturnNode = true;

         LatticeValue v = null;
         if (node.in != null) {
            v = node.in.get(varName);
         }
         if (v == null && node.out != null) {
            v = node.out.get(varName);
         }

         if (v == null || !v.isConstant()) {
            return fallback;
         }

         if (agreed == null) {
            agreed = v;
         } else if (!sameConstant(agreed, v)) {
            return fallback;
         }
      }

      if (!sawReturnNode || agreed == null) {
         return fallback;
      }

      if (agreed.getType() == LatticeValue.Type.INT) {
         return String.valueOf(agreed.getIntValue());
      }
      if (agreed.getType() == LatticeValue.Type.BOOLEAN) {
         return String.valueOf(agreed.getBoolValue());
      }
      return fallback;
   }

   private boolean sameConstant(LatticeValue a, LatticeValue b) {
      if (a == null || b == null || !a.isConstant() || !b.isConstant()) {
         return false;
      }
      if (a.getType() != b.getType()) {
         return false;
      }
      if (a.getType() == LatticeValue.Type.INT) {
         return a.getIntValue() == b.getIntValue();
      }
      if (a.getType() == LatticeValue.Type.BOOLEAN) {
         return a.getBoolValue() == b.getBoolValue();
      }
      return false;
   }
   
   /**
    * Extract method signature from a MessageSend expression
    * Returns "ClassName.methodName" or "unknown" if cannot determine
    */
   private String extractMethodSignatureFromMessageSend(MessageSend msgSend) {
      try {
         // Get method name (f2 -> Identifier)
         String methodName = msgSend.f2.f0.tokenImage;
         
         // Get the object being called (f0 -> PrimaryExpression)
         PrimaryExpression primExpr = msgSend.f0;
         if (primExpr == null || primExpr.f0 == null) {
            return "unknown";
         }
         
         Object primChoice = primExpr.f0.choice;
         String className = null;
         
         // Case 1: this.method()
         if (primChoice instanceof ThisExpression) {
            className = currClass;
         }
         // Case 2: identifier.method() - try to resolve the type
         else if (primChoice instanceof Identifier) {
            Identifier objId = (Identifier) primChoice;
            String objName = objId.f0.tokenImage;
            
            // Try to find the type from symbol table
            if (SymbolTable.class_map.containsKey(currClass)) {
               class_data classData = SymbolTable.class_map.get(currClass);
               if (classData != null) {
                  // 1) Local variables in current method
                  Method_data methodData = classData.m_methods.get(currMethod);
                  if (methodData != null) {
                     if (methodData.m_vars.containsKey(objName)) {
                        className = methodData.m_vars.get(objName).data_type;
                     } else if (methodData.m_args.containsKey(objName)) {
                        className = methodData.m_args.get(objName).data_type;
                     }
                  }

                  // 2) Class fields
                  if ((className == null || className.isEmpty()) && classData.m_fields.containsKey(objName)) {
                     className = classData.m_fields.get(objName).data_type;
                  }
               }
            }
         }
         
         if (className != null && !className.isEmpty()) {
            return className + "." + methodName;
         }
         
         return "unknown";
         
      } catch (Exception e) {
         return "unknown";
      }
   }
   
   /**
    * Check if a variable is live after a given CFGNode
    * Uses liveness analysis from pass3 to check if variable is in LiveOut set
    * Uses node LABELS to match nodes (not object identity)
    */
   private boolean isVariableLive(String varName, CFGNode cfgNode) {
      // If no CFG node, use conservative approach
      if (cfgNode == null) {
         // System.out.println("[LIVE] " + varName + ": cfgNode is null");
         return true;
      }
      
      if (currMethod == null || currMethod.isEmpty()) {
         // System.out.println("[LIVE] " + varName + ": currMethod=" + currMethod + " returning true (conservative)");
         return true;
      }
      
      String methodSig = currClass + "." + currMethod;
      // System.out.println("[LIVE] " + varName + ": Looking up in liveness analysis for " + methodSig);
      
      // Check if liveness analysis data exists for this method
      if (!pass3.livenessAnalysis.containsKey(methodSig)) {
         // System.out.println("[LIVE] " + varName + ": No liveness data for " + methodSig + ", available keys: " + pass3.livenessAnalysis.keySet());
         return true;
      }
      
      Map<CFGNode, Set<String>> livenessMap = pass3.livenessAnalysis.get(methodSig);
      
      // Find matching node by label (not by object identity)
      String nodeLabel = cfgNode.getLabel();
      CFGNode matchingNode = null;
      for (CFGNode node : livenessMap.keySet()) {
         if (node.getLabel().equals(nodeLabel)) {
            matchingNode = node;
            break;
         }
      }
      
      if (matchingNode == null) {
         // System.out.println("[LIVE] " + varName + ": No matching node with label " + nodeLabel + " in liveness map");
         return true;  // Conservative
      }
      
      // Check if variable is in LiveOut set of this node
      Set<String> liveOut = livenessMap.get(matchingNode);
      boolean live = liveOut.contains(varName);
      // System.out.println("[LIVE] " + varName + " at node " + nodeLabel + ": LiveOut=" + liveOut + " -> isLive=" + live);
      return live;
   }

   /**
    * Conservative guard: keep assignments to variables used in control-flow headers.
    * This prevents generating code with uninitialized if/while/for condition variables.
    */
   private boolean isControlFlowHeaderVariable(String varName) {
      if (currCFG == null || varName == null || varName.isEmpty()) {
         return false;
      }

      for (CFGNode node : currCFG.getNodes()) {
         Node control = node.getControlFlowNode();
         if (control instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement) control;
            if (ifStmt.f2 != null && ifStmt.f2.f0 != null && varName.equals(ifStmt.f2.f0.tokenImage)) {
               return true;
            }
         } else if (control instanceof WhileStatement) {
            WhileStatement whileStmt = (WhileStatement) control;
            if (whileStmt.f2 != null && whileStmt.f2.f0 != null && varName.equals(whileStmt.f2.f0.tokenImage)) {
               return true;
            }
         } else if (control instanceof ForStatement) {
            ForStatement forStmt = (ForStatement) control;

            // for (i = expr; cond; j = expr)
            if (forStmt.f2 != null && forStmt.f2.f0 != null && varName.equals(forStmt.f2.f0.tokenImage)) {
               return true;
            }
            if (forStmt.f8 != null && forStmt.f8.f0 != null && varName.equals(forStmt.f8.f0.tokenImage)) {
               return true;
            }
            if (expressionUsesVariable(forStmt.f4, varName)
                  || expressionUsesVariable(forStmt.f6, varName)
                  || expressionUsesVariable(forStmt.f10, varName)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Returns true if expression reads the given variable name.
    */
   private boolean expressionUsesVariable(Expression expr, String varName) {
      if (expr == null || expr.f0 == null || varName == null || varName.isEmpty()) {
         return false;
      }

      Object choice = expr.f0.choice;

      if (choice instanceof PrimaryExpression) {
         PrimaryExpression prim = (PrimaryExpression) choice;
         return primaryExpressionUsesVariable(prim, varName);
      } else if (choice instanceof CompareExpression) {
         CompareExpression cmp = (CompareExpression) choice;
         return constOrIdUsesVariable(cmp.f0, varName) || constOrIdUsesVariable(cmp.f2, varName);
      } else if (choice instanceof PlusExpression) {
         PlusExpression plus = (PlusExpression) choice;
         return constOrIdUsesVariable(plus.f0, varName) || constOrIdUsesVariable(plus.f2, varName);
      } else if (choice instanceof MinusExpression) {
         MinusExpression minus = (MinusExpression) choice;
         return constOrIdUsesVariable(minus.f0, varName) || constOrIdUsesVariable(minus.f2, varName);
      } else if (choice instanceof TimesExpression) {
         TimesExpression times = (TimesExpression) choice;
         return constOrIdUsesVariable(times.f0, varName) || constOrIdUsesVariable(times.f2, varName);
      } else if (choice instanceof ArrayLookup) {
         ArrayLookup lookup = (ArrayLookup) choice;
         return varName.equals(lookup.f0.f0.tokenImage) || constOrIdUsesVariable(lookup.f2, varName);
      } else if (choice instanceof ArrayLength) {
         ArrayLength len = (ArrayLength) choice;
         return varName.equals(len.f0.f0.tokenImage);
      } else if (choice instanceof MessageSend) {
         MessageSend msg = (MessageSend) choice;
         if (primaryExpressionUsesVariable(msg.f0, varName)) {
            return true;
         }
         if (msg.f4.present()) {
            Node argNode = msg.f4.node;
            if (argNode instanceof ArgList) {
               ArgList argList = (ArgList) argNode;
               if (constOrIdUsesVariable(argList.f0, varName)) {
                  return true;
               }
               if (argList.f1.present()) {
                  Enumeration<Node> rests = argList.f1.elements();
                  while (rests.hasMoreElements()) {
                     ArgRest rest = (ArgRest) rests.nextElement();
                     if (constOrIdUsesVariable(rest.f1, varName)) {
                        return true;
                     }
                  }
               }
            }
         }
      } else if (choice instanceof FieldRead) {
         FieldRead fieldRead = (FieldRead) choice;
         return varName.equals(fieldRead.f0.f0.tokenImage);
      }

      return false;
   }

   private boolean constOrIdUsesVariable(ConstOrId c, String varName) {
      if (c == null || c.f0 == null || varName == null || varName.isEmpty()) {
         return false;
      }
      Object choice = c.f0.choice;
      return (choice instanceof Identifier) && varName.equals(((Identifier) choice).f0.tokenImage);
   }

   private boolean primaryExpressionUsesVariable(PrimaryExpression prim, String varName) {
      if (prim == null || prim.f0 == null || varName == null || varName.isEmpty()) {
         return false;
      }
      Object choice = prim.f0.choice;
      if (choice instanceof Identifier) {
         return varName.equals(((Identifier) choice).f0.tokenImage);
      }
      if (choice instanceof NotExpression) {
         NotExpression notExpr = (NotExpression) choice;
         return notExpr.f1 != null && notExpr.f1.f0 != null && varName.equals(notExpr.f1.f0.tokenImage);
      }
      if (choice instanceof ArrayAllocationExpression) {
         ArrayAllocationExpression arrAlloc = (ArrayAllocationExpression) choice;
         return constOrIdUsesVariable(arrAlloc.f3, varName);
      }
      return false;
   }
   
   /**
    * Fallback method to find CFGNode for an assignment to a variable
    * Searches through CFG nodes by matching statement pattern
    */
   private CFGNode findCFGNodeForAssignment(String varName) {
      if (currCFG == null) {
         return null;
      }
      
      // Search through all nodes in the CFG to find one with assignment to varName
      for (CFGNode node : currCFG.getNodes()) {
         List<Statement> stmts = node.getStatements();
         if (stmts == null || stmts.isEmpty()) continue;
         
         // Check LAST statement in this node (most likely to be the one we want)
         for (int i = stmts.size() - 1; i >= 0; i--) {
            Statement stmt = stmts.get(i);
            if (stmt == null || stmt.f0 == null) continue;
            
            Object choice = stmt.f0.choice;
            if (choice instanceof AssignmentStatement) {
               AssignmentStatement assign = (AssignmentStatement) choice;
               if (assign.f0.f0.tokenImage.equals(varName)) {
                  // System.out.println("[FALLBACK] Found node for " + varName + ": " + node.getLabel());
                  return node;
               }
            }
         }
      }
      
      // System.out.println("[FALLBACK] Could not find node for " + varName);
      return null;
   }

}
