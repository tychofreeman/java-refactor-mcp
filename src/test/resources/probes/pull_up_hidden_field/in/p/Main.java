package p;
public class Main {
  public static void main(String[] args) {
    System.out.println(new Sub().show());
  }
}
class Base {
  String label = "base";
}
class Sub extends Base {
  String label = "sub";
  String show() { return label; }
}
