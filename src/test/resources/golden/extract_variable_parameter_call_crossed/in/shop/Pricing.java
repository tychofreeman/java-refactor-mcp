package shop;

class Pricing {
  private int count;

  int priceOf(Order order) {
    count++;
    return order.customer().length();
  }

  int use(Order o) {
    return priceOf(o);
  }
}
