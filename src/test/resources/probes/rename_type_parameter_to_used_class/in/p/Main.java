package p;
public class Main {
  public static void main(String[] args) {
    System.out.println(new Box<String>().label("x"));
  }
}
class Box<T> {
  String label(Object o) { return o instanceof Item ? "item" : "other"; }
}
class Item {
}
