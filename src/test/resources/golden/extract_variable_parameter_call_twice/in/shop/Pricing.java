package shop;

class Pricing {
  int priceOf(Order order) {
    int a = order.customer().length();
    return a + order.customer().hashCode();
  }

  int use(Order o) {
    return priceOf(o);
  }
}
