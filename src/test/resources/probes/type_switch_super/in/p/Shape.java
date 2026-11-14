package p;

abstract sealed class Shape permits Circle, Square {
  String describe() {
    return "shape";
  }
}
