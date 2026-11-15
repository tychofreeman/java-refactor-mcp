package p;

class Shapes {
  sealed interface Shape permits Figures.Circle, Shapes.Square {}

  record Square(double side) implements Shape {}

  static double area(Shape s) {
    return s instanceof Figures.Circle c ? 3 * c.r() * c.r() : 1;
  }
}
