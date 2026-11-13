package shop;

class Line {
  private final String who;

  Line(Order order) {
    who = order.customer();
  }

  static Line of(Order o) {
    return new Line(o);
  }
}
