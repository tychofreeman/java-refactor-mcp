package p;
public class Main {
  public static void main(String[] args) {
    System.out.println(new Base().run("a") + " " + new Sub().run("b"));
  }
}
class Base {
  String run(String r) {
    String s = "base " + r;
    return step(s);
  }
  String step(String s) {
    return s + "!";
  }
}
class Sub extends Base {
  @Override
  String run(String r) {
    String s = "sub " + r;
    return s + "?";
  }
}
