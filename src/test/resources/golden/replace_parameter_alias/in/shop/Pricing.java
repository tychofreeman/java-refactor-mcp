package shop;

class Pricing {
  private final Rates rates = new Rates();

  /**
   * The price of an order.
   *
   * @param order the order
   * @param customer who ordered it
   */
  int priceOf(Order order, String customer) {
    return rates.rateFor(customer) * order.amount();
  }

  static class Rates {
    int rateFor(String customer) {
      return customer.length();
    }
  }
}
