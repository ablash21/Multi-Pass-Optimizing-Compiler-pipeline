package visitor;
import syntaxtree.*;
import java.util.*;
public class class_data {

      public String name ; 
      public HashMap<String, var_data> m_fields; 
      public HashMap<String, Method_data> m_methods; 
      public String parent_name ;
      
      public class_data( String name ) 
      {
         this.name = name;
         parent_name = null ;
         m_fields = new HashMap<String, var_data>();
         m_methods = new HashMap<String, Method_data>();

      }
}