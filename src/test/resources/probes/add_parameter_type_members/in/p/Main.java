package p;
public class Main {
  public static void main(String[] args) {
    Box a = new Box("a");
    Box b = a.copy();
    System.out.println(a.get() + " " + b.get());
    System.out.println(a.same(b) + " " + a.same(new Box("a")));
    System.out.println(b);
    System.out.println(new Box("x").get().length());
  }
}
