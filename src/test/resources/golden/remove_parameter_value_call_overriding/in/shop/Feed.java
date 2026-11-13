package shop;

class Feed {
  static class Shape {
    String describe() {
      return "shape";
    }
  }

  static class Square extends Shape {
    @Override
    String describe() {
      return "square";
    }
  }

  String send(Shape shape, String text) {
    return text + "!";
  }

  String run(Square square, Shape shape) {
    return send(square, square.describe()) + send(shape, shape.describe());
  }
}
