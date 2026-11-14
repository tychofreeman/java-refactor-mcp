package p;
public class Main {
  public static void main(String[] args) {
    System.out.println(new C().f());
    System.out.println(new D().f());
  }
}
class A {
  int f() { return 1; }
}
class B extends A {
}
class C extends B {
  int f() { return 2; }
}
class D extends C {
}
