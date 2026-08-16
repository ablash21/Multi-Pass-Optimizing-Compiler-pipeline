import syntaxtree.*;
import visitor.*;

public class P4 {
   public static void main(String [] args) {
      try {
         Goal root = new FunkyTacoJavaParser(System.in).Goal();

         root.accept(new Pass1(), null);

         Pass2 p2 = new Pass2();
         root.accept(p2, null);

         PointsToEngine engine = new PointsToEngine();
         engine.run();

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


