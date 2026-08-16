
package visitor;
import java.util.HashMap;

public class SymbolTable {
    public static HashMap<String, class_data> class_map = new HashMap<>();
    public  static boolean final_var_error = false;
    public  static boolean uninitialized_var_error = false; 

}