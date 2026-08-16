package visitor;
import syntaxtree.*;
import java.util.*;

/**
 * Pass 2: Call Graph Construction + CHA-based Reachability
 *
 * Improvements over original:
 *  1. computeReachableMethods does BFS from main then prunes dead call-graph
 *     edges (pruneDeadCallEdges) so pass3 side-effect analysis is not
 *     contaminated by unreachable impure callees.
 *  2. resolveMethodClass now checks method locals and params (not just class
 *     fields) enabling precise CHA resolution for local-object calls.
 */
public class pass2<R,A> implements GJVisitor<R,A> {
   
   private String currClass = null;
   private String currMethod = null;
   
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

   /**
    * f0 -> MainClass()  f1 -> ( TypeDeclaration() )*  f2 -> <EOF>
    */
   public R visit(Goal n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      
      // Phase 1: Compute reachable methods after call graph is fully built
      computeReachableMethods();
      
      return _ret;
   }

   public R visit(MainClass n, A argu) {
      R _ret=null;
      String className = ((Identifier)n.f1).f0.tokenImage;
      currClass = className;
      currMethod = "main";
      
      String methodSig = className + "." + currMethod;
      SymbolTable.callGraph.putIfAbsent(methodSig, new HashSet<>());
      
      n.f14.accept(this, argu);
      n.f15.accept(this, argu);
      
      currClass = null;
      currMethod = null;
      return _ret;
   }

   public R visit(TypeDeclaration n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   public R visit(ClassDeclaration n, A argu) {
      R _ret=null;
      String className = ((Identifier)n.f1).f0.tokenImage;
      currClass = className;
      n.f4.accept(this, argu);
      currClass = null;
      return _ret;
   }

   public R visit(ClassExtendsDeclaration n, A argu) {
      R _ret=null;
      String className = ((Identifier)n.f1).f0.tokenImage;
      currClass = className;
      n.f6.accept(this, argu);
      currClass = null;
      return _ret;
   }

   public R visit(VarDeclaration n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   public R visit(MethodDeclaration n, A argu) {
      R _ret=null;
      String methodName = ((Identifier)n.f2).f0.tokenImage;
      currMethod = methodName;
      
      String methodSig = currClass + "." + currMethod;
      SymbolTable.callGraph.putIfAbsent(methodSig, new HashSet<>());
      
      SymbolTable.classMethodsMap.putIfAbsent(currClass, new HashSet<>());
      SymbolTable.classMethodsMap.get(currClass).add(methodName);
      
      n.f8.accept(this, argu);
      
      currMethod = null;
      return _ret;
   }

   public R visit(FormalParameterList n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   public R visit(FormalParameter n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   public R visit(FormalParameterRest n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   public R visit(Type n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   public R visit(ArrayType n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   public R visit(BooleanType n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   public R visit(IntegerType n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   public R visit(Statement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   public R visit(Block n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   public R visit(AssignmentStatement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      return _ret;
   }

   public R visit(VoidMessageSendStatement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

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

   public R visit(WhileStatement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      return _ret;
   }

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

   public R visit(PrintStatement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      return _ret;
   }

   public R visit(Expression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   public R visit(CompareExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   public R visit(PlusExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   public R visit(MinusExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   public R visit(TimesExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   public R visit(ArrayLookup n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      return _ret;
   }

   public R visit(ArrayLength n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   public R visit(FieldRead n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   /**
    * IMPROVED MessageSend visitor: build call-graph edges using CHA.
    * resolveMethodClass now checks locals, params, and fields.
    */
   public R visit(MessageSend n, A argu) {
      R _ret=null;
      
      String calledMethodName = ((Identifier)n.f2).f0.tokenImage;
      Set<String> possibleClasses = resolveMethodClassesWithCHA(n.f0, calledMethodName);
      
      if (currClass != null && currMethod != null && possibleClasses != null && !possibleClasses.isEmpty()) {
         String callerSig = currClass + "." + currMethod;
         SymbolTable.callGraph.putIfAbsent(callerSig, new HashSet<>());
         for (String targetClass : possibleClasses) {
            String calleeSig = targetClass + "." + calledMethodName;
            SymbolTable.callGraph.get(callerSig).add(calleeSig);
         }
      }
      
      n.f0.accept(this, argu);
      n.f4.accept(this, argu);
      
      return _ret;
   }
   
   /**
    * CHA: collect all classes that implement methodName starting from the
    * statically resolved receiver class.
    */
   private Set<String> resolveMethodClassesWithCHA(PrimaryExpression receiver, String methodName) {
      Set<String> possibleClasses = new HashSet<>();
      String baseClass = resolveMethodClass(receiver);
      if (baseClass == null) return possibleClasses;
      
      class_data baseCd = SymbolTable.class_map.get(baseClass);
      if (baseCd != null && baseCd.m_methods.containsKey(methodName)) {
         possibleClasses.add(baseClass);
      }
      
      for (String subclass : getSubclassesRecursive(baseClass)) {
         class_data subCd = SymbolTable.class_map.get(subclass);
         if (subCd != null && subCd.m_methods.containsKey(methodName)) {
            possibleClasses.add(subclass);
         }
      }
      
      return possibleClasses;
   }
   
   private Set<String> getSubclassesRecursive(String className) {
      Set<String> all = new HashSet<>();
      Set<String> direct = SymbolTable.classChildren.get(className);
      if (direct != null) {
         for (String child : direct) {
            all.add(child);
            all.addAll(getSubclassesRecursive(child));
         }
      }
      return all;
   }
   
   /**
    * IMPROVED: resolves receiver type by checking local vars, params, and
    * class fields (the original only checked fields).
    */
   private String resolveMethodClass(PrimaryExpression receiver) {
      if (receiver.f0.which == 4) {
         // ThisExpression
         return currClass;
      } else if (receiver.f0.which == 3) {
         // Identifier - look up variable type
         Identifier id = (Identifier) receiver.f0.choice;
         String varName = id.f0.tokenImage;
         
         if (currClass != null && currMethod != null) {
            class_data cd = SymbolTable.class_map.get(currClass);
            if (cd != null) {
               Method_data md = cd.m_methods.get(currMethod);
               if (md != null) {
                  // 1) Local variables
                  var_data vd = md.m_vars.get(varName);
                  if (vd != null) return vd.data_type;
                  // 2) Method parameters
                  vd = md.m_args.get(varName);
                  if (vd != null) return vd.data_type;
               }
               // 3) Class fields
               var_data vd = cd.m_fields.get(varName);
               if (vd != null) return vd.data_type;
            }
         }
      }
      return null;
   }

   public R visit(ArgList n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   public R visit(ArgRest n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   public R visit(PrimaryExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   public R visit(IntegerLiteral n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   public R visit(PlainIntegerLiteral n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   public R visit(IntegerLiteralWithPosSign n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   public R visit(IntegerLiteralWithNegSign n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   public R visit(ConstOrId n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   public R visit(TrueLiteral n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   public R visit(FalseLiteral n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   public R visit(Identifier n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   public R visit(ThisExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   public R visit(ArrayAllocationExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      return _ret;
   }

   public R visit(AllocationExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      return _ret;
   }

   public R visit(NotExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   // -----------------------------------------------------------------------
   //  IMPROVED computeReachableMethods
   // -----------------------------------------------------------------------

   /**
    * BFS reachability from main, then prune dead call-graph edges.
    *
    * Pruning dead edges ensures pass3's side-effect analysis is not
    * contaminated by unreachable impure callees.  Without pruning,
    * a method A that only calls dead method B (which has a print) would
    * be marked impure even though B can never execute.
    */
   private static void computeReachableMethods() {
      SymbolTable.reachableMethods.clear();
      SymbolTable.unreachableMethods.clear();
      
      // Find main method
      String mainMethodSig = null;
      for (String methodSig : SymbolTable.callGraph.keySet()) {
         if (methodSig.endsWith(".main")) {
            mainMethodSig = methodSig;
            break;
         }
      }
      
      if (mainMethodSig == null) {
         System.err.println("WARNING: Main method not found in call graph");
         return;
      }
      
      // BFS from main
      Queue<String> queue = new LinkedList<>();
      Set<String> visited = new HashSet<>();
      
      queue.add(mainMethodSig);
      visited.add(mainMethodSig);
      SymbolTable.reachableMethods.add(mainMethodSig);
      
      while (!queue.isEmpty()) {
         String current = queue.poll();
         Set<String> callees = SymbolTable.callGraph.get(current);
         if (callees != null) {
            for (String callee : callees) {
               if (!visited.contains(callee)) {
                  visited.add(callee);
                  SymbolTable.reachableMethods.add(callee);
                  queue.add(callee);
               }
            }
         }
      }
      
      // Mark unreachable
      for (String methodSig : SymbolTable.callGraph.keySet()) {
         if (!SymbolTable.reachableMethods.contains(methodSig)) {
            SymbolTable.unreachableMethods.add(methodSig);
         }
      }
      
      // IMPROVEMENT: remove edges pointing to unreachable methods so
      // pass3's side-effect fixpoint gives accurate results.
      pruneDeadCallEdges();
   }

   /**
    * Remove call-graph edges whose targets are unreachable.
    * This is sound: dead methods can never execute, so their
    * (potential) side effects cannot be observed.
    */
   private static void pruneDeadCallEdges() {
      for (String caller : SymbolTable.callGraph.keySet()) {
         Set<String> callees = SymbolTable.callGraph.get(caller);
         if (callees != null) {
            callees.removeIf(callee -> SymbolTable.unreachableMethods.contains(callee));
         }
      }
   }
}