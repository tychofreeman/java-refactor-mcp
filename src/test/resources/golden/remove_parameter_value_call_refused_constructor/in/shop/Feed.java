package shop;

class Feed {
  private final StringBuilder log = new StringBuilder("feed");
  private final String line;

  Feed(Order order, String customer) {
    this.line = customer + " " + order.amount();
  }

  static Feed of(Order order) {
    return new Feed(order, order.customer());
  }
}
