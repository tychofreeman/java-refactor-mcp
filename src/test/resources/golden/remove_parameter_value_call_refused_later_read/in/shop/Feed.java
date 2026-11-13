package shop;

class Feed {
  private int sent;

  String send(Order order, String customer, int number) {
    sent++;
    return customer + " " + order.amount() + " " + number;
  }

  String run(Order order) {
    return send(order, order.customer(), sent);
  }
}
