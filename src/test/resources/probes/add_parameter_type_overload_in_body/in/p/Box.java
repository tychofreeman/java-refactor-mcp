package p;
public class Box {
  private final String value;
  public Box(String value) { this.value = value; }
  void show() { print(value); }
  static void print(String s) { System.out.println("string " + s); }
  static void print(Object o) { System.out.println("object " + o); }
}
