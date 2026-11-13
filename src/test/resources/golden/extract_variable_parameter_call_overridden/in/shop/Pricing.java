package shop;

class Pricing {
  int priceOf(Order order) {
    return order.customer().length();
  }

  int use(Order o) {
    return priceOf(o);
  }
}
