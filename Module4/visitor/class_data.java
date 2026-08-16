package visitor;
import java.util.*;

public class class_data {
    public String name;
    public HashMap<String, var_data> m_fields;
    public HashMap<String, Method_data> m_methods;
    public String parent_name;   // null if no parent

    public class_data(String name) {
        this.name = name;
        parent_name = null;
        m_fields = new HashMap<>();
        m_methods = new HashMap<>();
    }
}
