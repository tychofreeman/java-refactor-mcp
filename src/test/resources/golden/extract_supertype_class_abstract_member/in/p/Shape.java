package p;

abstract class Shape {
  abstract double area();

  String describe() {
    return "area " + area();
  }
}
