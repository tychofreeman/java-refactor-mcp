package p;
public class Main {
  public static void main(String[] args) {
    Base b = new Sub();
    System.out.println(((Sub) b).hello());
  }
}
class Base {
  String hello() { return "hi"; }
}
class Sub extends Base {
}
