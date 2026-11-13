package shop;

class Pricing {
  int priceOf(Order order) {
    return rate(order) * 2;
  }

  int rate(Order order) {
    return order.customer().length();
  }

  class Line {
    int rate(Order order) {
      return 0;
    }

    int cost(Order o) {
      return priceOf(o);
    }
  }
}
