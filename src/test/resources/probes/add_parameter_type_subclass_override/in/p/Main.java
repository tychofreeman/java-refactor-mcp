package p;
public class Main {
  static class Named extends Box {
    Named() { super("n"); }
    @Override public String get() { return "named " + super.get(); }
    @Override public Box copy() { return new Named(); }
  }
  public static void main(String[] args) {
    Box b = new Named();
    System.out.println(b.get());
    System.out.println(b.copy().get());
    b.show(b);
    b.show(new Box("x").copy());
    Object o = b;
    if (o instanceof Box c) { c.show((Object) "o"); c.show(c); }
  }
}
