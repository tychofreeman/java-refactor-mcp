package p;
public class Main {
  static void show(Shape s) {
    if (s instanceof Polygon p) {
      System.out.println("poly");
    } else if (s instanceof Square q) {
      System.out.println("square");
    }
  }
  public static void main(String[] args) {
    show(new Square());
    show(new Polygon());
  }
}
