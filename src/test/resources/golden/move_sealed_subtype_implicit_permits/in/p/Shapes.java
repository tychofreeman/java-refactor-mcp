package p;

public class Shapes {
  sealed interface Shape {}

  static final class Circle implements Shape {}

  static final class Square implements Shape {}

  private static final class Hidden implements Shape {}

  static int count(Shape s) {
    return s instanceof Circle ? 1 : 0;
  }
}
