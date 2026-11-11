package p;
public class Main {
  public static void main(String[] args) {
    System.out.println(new Dry().run("a") + " " + new Real().run("b"));
  }
}
abstract class Base {
  abstract String run(String r);
}
class Dry extends Base {
  @Override
  String run(String r) { return "dry " + r; }
}
class Real extends Base {
  @Override
  String run(String r) { return "real " + r; }
}
