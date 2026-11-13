package shop;

class Feed {
  static String label(Order order) {
    return order == null ? "none" : "order";
  }

  String send(Order order, String label) {
    return label + " " + order.amount();
  }

  String run(Order order) {
    return send(order, label(order));
  }
}
