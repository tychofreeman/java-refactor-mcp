package p;
public class Box {
  private final String value;
  public Box(String value) { this.value = value; }
  public String get() { return value; }
  public Box copy() { return new Box(value); }
  public boolean same(Box other) { return other.value.equals(value); }
  public String toString() { return "box " + value.hashCode() + " " + value; }
}
