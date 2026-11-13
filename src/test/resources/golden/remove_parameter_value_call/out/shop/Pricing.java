package shop;

class Pricing {
  private final Rates rates = new Rates();

  /**
   * The price of an order.
   *
   * @param order the order
   */
  int priceOf(Order order) {
    String customer = order.customer();
    return rates.rateFor(customer) * order.amount();
  }

  static class Rates {
    int rateFor(String customer) {
      return customer.length();
    }
  }
}
