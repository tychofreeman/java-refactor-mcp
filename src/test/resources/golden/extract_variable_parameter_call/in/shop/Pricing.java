package shop;

class Pricing {
  int priceOf(Order order) {
    return Rates.rateFor(order.customer()) * order.amount();
  }

  static class Rates {
    static int rateFor(String customer) {
      return customer.length();
    }
  }
}
