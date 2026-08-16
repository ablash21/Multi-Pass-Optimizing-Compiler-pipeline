import syntaxtree.*;
import visitor.*;

/**
 * P4 — driver for the function-inlining compiler.
 *
 *   java P4 < P.java > Pf.java
 *
 * Pipeline:
 *   Pass 1: ClassHierarchyVisitor  -> SymbolTable
 *   Pass 2: IRBuilderVisitor       -> Method_data.ir + nodeToSite
 *   Pass 3: PointsToEngine         -> Map<MessageSendStatement, InlineDecision>
 *   Pass 4: PrettyPrinter+Inliner  -> output FunkyTACoJava source
 */
public class P4 {
    public static void main(String[] args) {
        try {
            // Parse FunkyTACoJava from stdin.
            Goal root = new FunkyTacoJavaParser(System.in).Goal();

            // Pass 1: build the symbol table.
            root.accept(new Pass1(), null);

            // Pass 2: build the IR for every method.
            Pass2 p2 = new Pass2();
            root.accept(p2, null);

            // Pass 3: interprocedural points-to + monomorphism + cycle filter.
            PointsToEngine engine = new PointsToEngine();
            engine.run();

            // Pass 4: pretty-print with inlining applied at monomorphic sites.
            Pass4 printer = new Pass4(engine.decisions);
            String output = root.accept(printer, null);

            System.out.print(output);
        } catch (Throwable e) {
            System.err.println("P4 error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
