package shop;

class Checkout {
  private final Pricing pricing = new Pricing();

  int total(Order order) {
    Pricing local = new Pricing();
    return pricing.priceOf(order) + local.priceOf(order) + this.pricing.priceOf(order);
  }
}
