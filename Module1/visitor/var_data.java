package visitor;
import syntaxtree.*;
import java.util.*;
public class var_data
{
      String name ;
      String data_type ;
      boolean is_final ;
      boolean is_initialized ;
      public var_data( String name, String data_type )
      {
         this.name = name;
         this.data_type = data_type;
         this.is_final = false;
         this.is_initialized = false;
      }
}