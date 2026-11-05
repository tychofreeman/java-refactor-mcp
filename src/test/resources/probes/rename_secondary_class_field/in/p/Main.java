package p;
public class Main {
  public static void main(String[] args) {
    System.out.println(new Base().a + new Base().show());
  }
}
class Base {
  String a = "base";
  String show() { return a + "!"; }
}
