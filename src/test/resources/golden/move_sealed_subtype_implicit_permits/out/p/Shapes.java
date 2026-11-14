package p;

public class Shapes {
  sealed interface Shape permits Shapes.Circle, Square, Shapes.Hidden {}

  static final class Circle implements Shape {}

  private static final class Hidden implements Shape {}

  static int count(Shape s) {
    return s instanceof Circle ? 1 : 0;
  }
}
