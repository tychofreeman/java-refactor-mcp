package shop;

class Feed {
  private int marks;

  int mark() {
    return ++marks;
  }

  String send(Order order, String customer, int mark) {
    return customer + " " + order.amount() + " " + mark;
  }

  String run(Order order) {
    return send(order, order.customer(), mark());
  }
}
