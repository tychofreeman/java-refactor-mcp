package shop;

class Pricing {
  int priceOf(Order order) {
    return rate(order) * 2;
  }

  int rate(Order order) {
    return order.customer().length();
  }

  int use(Order o) {
    return priceOf(o);
  }
}
