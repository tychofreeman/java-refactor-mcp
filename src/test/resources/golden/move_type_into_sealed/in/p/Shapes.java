package p;

class Shapes {
  sealed interface Shape {}

  record Circle(double r) implements Shape {}

  record Square(double side) implements Shape {}

  static double area(Shape s) {
    return s instanceof Circle c ? 3 * c.r() * c.r() : 1;
  }
}
