package shop;

class Shop {
  int priceOf(Order order, int r) {
    return r + order.amount();
  }

  int rate(Order order) {
    return 1;
  }
}
