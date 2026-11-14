package p;

public class Outer {
  record Point(int x, int y) {}

  static class Calc {
    int sum(Point p) {
      return p.x + p.y;
    }
  }
}
