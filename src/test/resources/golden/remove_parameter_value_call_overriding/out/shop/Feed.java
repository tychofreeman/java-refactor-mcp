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

  String send(Shape shape) {
    String text = shape.describe();
    return text + "!";
  }

  String run(Square square, Shape shape) {
    return send(square) + send(shape);
  }
}
