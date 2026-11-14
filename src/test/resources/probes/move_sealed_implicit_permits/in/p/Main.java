package p;
public class Main {
  sealed interface Shape {}
  record Circle(int r) implements Shape {}
  private record Square(int side) implements Shape {}
  static int area(Shape s) {
    return switch (s) {
      case Circle c -> 3 * c.r() * c.r();
      case Square q -> q.side() * q.side();
    };
  }
  public static void main(String[] args) {
    System.out.println(area(new Circle(2)) + " " + area(new Square(3)));
  }
}
