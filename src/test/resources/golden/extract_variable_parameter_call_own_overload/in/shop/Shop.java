package shop;

class Shop {
  int priceOf(Order order) {
    return rate(order) + order.amount();
  }

  int rate(Order order) {
    return 1;
  }
}
