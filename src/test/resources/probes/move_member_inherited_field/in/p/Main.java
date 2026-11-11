package p;
public class Main {
  public static void main(String[] args) {
    Sub s = new Sub();
    s.label = "changed";
    System.out.println(s.go());
  }
}
class Base {
  String label = "base";
}
class Sub extends Base {
  private String show() { return label + "!"; }
  String go() { return show(); }
}
