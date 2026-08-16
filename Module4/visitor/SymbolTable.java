package visitor;
import java.util.*;

public class SymbolTable {
    public static HashMap<String, class_data> class_map = new HashMap<>();

    // walk up hierarchy to find which class defines a method
    public static String resolveMethod(String className, String methodName) {
        String current = className;
        while (current != null) {
            class_data cd = class_map.get(current);
            if (cd == null) return null;
            if (cd.m_methods.containsKey(methodName)) return current;
            current = cd.parent_name;
        }
        return null;
    }

    // get Method_data resolving through hierarchy (virtual dispatch)
    public static Method_data getMethod(String className, String methodName) {
        String owner = resolveMethod(className, methodName);
        if (owner == null) return null;
        return class_map.get(owner).m_methods.get(methodName);
    }

    // get all fields including inherited ones (parent first, child overrides)
    public static Map<String, var_data> getAllFields(String className) {
        LinkedHashMap<String, var_data> all = new LinkedHashMap<>();
        List<String> chain = new ArrayList<>();
        String current = className;
        while (current != null) {
            chain.add(current);
            class_data cd = class_map.get(current);
            if (cd == null) break;
            current = cd.parent_name;
        }
        Collections.reverse(chain);
        for (String c : chain) {
            class_data cd = class_map.get(c);
            if (cd != null) all.putAll(cd.m_fields);
        }
        return all;
    }

    // is `sub` a subtype of `sup`? (reflexive)
    public static boolean isSubtype(String sub, String sup) {
        if (sub == null || sup == null) return false;
        String cur = sub;
        while (cur != null) {
            if (cur.equals(sup)) return true;
            class_data cd = class_map.get(cur);
            if (cd == null) return false;
            cur = cd.parent_name;
        }
        return false;
    }

    public static boolean isClassType(String type) {
        if (type == null) return false;
        return !type.equals("int")
            && !type.equals("boolean")
            && !type.equals("int[]");
    }

    public static void dump() {
        for (Map.Entry<String, class_data> e : class_map.entrySet()) {
            class_data cd = e.getValue();
            System.err.println("CLASS: " + cd.name
                + (cd.parent_name != null ? " extends " + cd.parent_name : ""));
            for (var_data f : cd.m_fields.values())
                System.err.println("  FIELD: " + f.data_type + " " + f.name);
            for (Method_data m : cd.m_methods.values()) {
                System.err.println("  METHOD: " + m.return_type + " " + m.name
                    + "  (owner=" + m.owner_class + ")");
                for (String a : m.arg_order)
                    System.err.println("    PARAM: " + m.m_args.get(a).data_type
                        + " " + a);
                for (var_data v : m.m_vars.values())
                    System.err.println("    LOCAL: " + v.data_type + " " + v.name);
            }
        }
    }
}
