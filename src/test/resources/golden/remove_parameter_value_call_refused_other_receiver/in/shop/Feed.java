package shop;

class Feed {
  private final String prefix;

  Feed(String prefix) {
    this.prefix = prefix;
  }

  String name(Order order) {
    return prefix + order.amount();
  }

  String send(Order order, String customer) {
    return customer + " " + order.amount();
  }

  String run(Feed other, Order order) {
    return other.send(order, name(order));
  }
}
