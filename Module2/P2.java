import syntaxtree.*;
import visitor.*;

public class P2 {
   public static void main(String [] args) {
      try {
         Node root = new BuritoJavaParser(System.in).Goal();
         
         root.accept(new pass0<Object,Object>(), null); 
         root.accept(new pass1<Object,Object>(), null);
      }
      catch (ParseException e) {
         System.out.println("Parse error: " + e.getMessage());
      }
   }
} 


