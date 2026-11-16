package p;
public class Main {
  static Box a = new Box();
  static Box b = new Box();
  static Box pick(String why, Box box) {
    System.out.println(why);
    return box;
  }
  static int say(String why, int n) {
    System.out.println(why);
    return n;
  }
  public static void main(String[] args) {
    b.count = 3;
    a.count = b.count;
    a.count = say("value", a.count + b.count);
    pick("target", a).count = say("read", pick("source", b).count) * a.count;
    System.out.println(a.count + " " + b.count);
  }
}
class Box {
  int count;
}
