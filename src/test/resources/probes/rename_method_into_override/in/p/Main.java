package p;
public class Main {
  public static void main(String[] args) {
    Base b = new Sub();
    System.out.println(b.name() + " " + ((Sub) b).helper());
  }
}
class Base {
  String name() { return "base"; }
}
class Sub extends Base {
  String helper() { return "helper"; }
}
