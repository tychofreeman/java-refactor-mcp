package shop;

class Pricing {
  int priceOf(Order order, String customer) {
    return Rates.rateFor(customer) * order.amount();
  }

  static class Rates {
    static int rateFor(String customer) {
      return customer.length();
    }
  }
}
