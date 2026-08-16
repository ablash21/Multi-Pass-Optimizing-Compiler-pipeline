===== Symbol Table =====
Class: MyVisitor extends Visitor
  Fields:
    (none)
  Methods:
    int visit(Tree n)
      Locals:
        int nti
Class: TV
  Fields:
    (none)
  Methods:
    int Start()
      Locals:
        boolean ntb
        int nti
        Tree root
        MyVisitor v
Class: Tree
  Fields:
    boolean has_left
    boolean has_right
    int key
    Tree left
    Tree my_null
    Tree right
  Methods:
    boolean Compare(int num1, int num2)
      Locals:
        boolean ntb
        int nti
    boolean Delete(int v_key)
      Locals:
        boolean cont
        Tree current_node
        boolean found
        boolean is_root
        int key_aux
        boolean ntb
        Tree parent_node
    boolean GetHas_Left()
      Locals:
        (none)
    boolean GetHas_Right()
      Locals:
        (none)
    int GetKey()
      Locals:
        (none)
    Tree GetLeft()
      Locals:
        (none)
    Tree GetRight()
      Locals:
        (none)
    boolean Init(int v_key)
      Locals:
        (none)
    boolean Insert(int v_key)
      Locals:
        boolean cont
        Tree current_node
        int key_aux
        Tree new_node
        boolean ntb
    boolean Print()
      Locals:
        Tree current_node
        boolean ntb
    boolean RecPrint(Tree node)
      Locals:
        boolean ntb
    boolean Remove(Tree p_node, Tree c_node)
      Locals:
        int auxkey1
        int auxkey2
        boolean ntb
    boolean RemoveLeft(Tree p_node, Tree c_node)
      Locals:
        boolean ntb
    boolean RemoveRight(Tree p_node, Tree c_node)
      Locals:
        boolean ntb
    int Search(int v_key)
      Locals:
        boolean cont
        Tree current_node
        int ifound
        int key_aux
    boolean SetHas_Left(boolean val)
      Locals:
        (none)
    boolean SetHas_Right(boolean val)
      Locals:
        (none)
    boolean SetKey(int v_key)
      Locals:
        (none)
    boolean SetLeft(Tree ln)
      Locals:
        (none)
    boolean SetRight(Tree rn)
      Locals:
        (none)
    int accept(Visitor v)
      Locals:
        int nti
Class: TreeVisitor
  Fields:
    (none)
  Methods:
    void main()
      Locals:
        (none)
Class: Visitor
  Fields:
    Tree l
    Tree r
  Methods:
    int visit(Tree n)
      Locals:
        int nti
No issue with variables.
