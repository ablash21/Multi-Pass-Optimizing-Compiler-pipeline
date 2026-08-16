import syntaxtree.*;
import visitor.*;

public class P1 {
   public static void main(String [] args) {
      try {
         Node root = new BuritoJavaParser(System.in).Goal();
         
         root.accept(new pass1<Object,Object>(), null); 
         // pass1.printSymbolTable();
         root.accept(new pass2<Object,Object>(), null);
         // root.accept(new pass3<Object,Object>(), null);
         System.out.println("No issue with variables.");
      }
      catch (my_exception e) {
         if(SymbolTable.final_var_error) {
            System.out.println("Final variable being assigned.");
            // System.out.println(e.getMessage());
         }
         else if(SymbolTable.uninitialized_var_error) {
            System.out.println("Uninitialized variable found.");
            // System.out.println(e.getMessage());
         }
         
      }
      catch (ParseException e) {
         System.out.println("Parse error: " + e.getMessage());
      }
   }
} 


