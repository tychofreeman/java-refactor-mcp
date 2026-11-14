package p;

abstract class Shape extends Measured {
  String describe() {
    return "area " + area();
  }
}
