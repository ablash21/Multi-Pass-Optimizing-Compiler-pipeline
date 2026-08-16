package visitor;
import syntaxtree.*;
import java.util.*;
public class Method_data {

      public String name ; 
      public String return_type; 
      public HashMap<String, var_data> m_vars; 
      public HashMap<String, var_data> m_args; 

      public Method_data( String name, String return_type ) 
      {
         this.name = name;
         this.return_type = return_type;
         m_vars = new HashMap<String, var_data>();
         m_args = new HashMap<String, var_data>();

      }

}