 class test3 {
 public static void main (String [] args){
A temp10;
int temp11;

 temp10 = new A ( ) ;
 temp11 = temp10 . m1 (  ) ;
System.out.println ( temp11 ) ;
 }
 }

  class A {
 int i;

  public int m1 (){
int a ;

int temp10;
int temp11;
int temp12;
int temp13;
boolean temp14;
int temp15;
int temp16;
int temp17;
int temp18;
int temp19;
int temp20;
int temp21;
int temp22;
int temp23;

temp10 = 10;
 
a = temp10;
temp11 = 0;
temp15 = i;
temp16 = 10;
 temp12 = temp15;
temp13 = temp16;
temp14 = temp12 < temp13;
for ( i = temp11 ; temp14 ; i = temp19 ) {
temp22 = i;
System.out.println ( temp22 ) ;
temp20 = i;
temp21 = 1;
 temp17 = temp20;
temp18 = temp21;
temp19 = temp17 + temp18;
temp15 = i;
temp16 = 10;
 temp12 = temp15;
temp13 = temp16;
temp14 = temp12 < temp13;
}
temp23 = 2;
 return temp23;
 }

 }

  class B extends A {
 int c;

  public int m2 (){
int temp10;

 temp10 = 3;
 return temp10;
 }

  public int m3 (){
B temp10;
int temp11;

 temp10 = this;
 temp11 = temp10 . m2 (  ) ;
 return temp11;
 }

 }

