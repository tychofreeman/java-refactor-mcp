package shop;

class Shop {
  int rate(Order o) {
    return 1;
  }

  int priceOf(Order order) {
    return rate(order) * 2;
  }
}
