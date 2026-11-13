package shop;

class Pricing {
  int priceOf(Order order, int r) {
    return r * 2;
  }

  int rate(Order order) {
    return order.customer().length();
  }

  int use(Order o) {
    return priceOf(o, rate(o)) + this.priceOf(o, rate(o));
  }
}
