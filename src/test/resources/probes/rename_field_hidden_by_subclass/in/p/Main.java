package p;
public class Main {
  public static void main(String[] args) {
    System.out.println(new Sub().get());
  }
}
class Base {
  String a = "base";
}
class Sub extends Base {
  String b = "sub";
  String get() { return a; }
}
