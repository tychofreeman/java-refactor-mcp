package p;
public class Box {
  private final String value;
  public Box(String value) { this.value = value; }
  public String get() { return value; }
  public Box copy() { return new Box(value); }
  void show(Object o) { System.out.println("object " + o); }
  void show(Box b) { System.out.println("box " + b.get()); }
}
