package shop;

class Checkout {
  private final Pricing pricing = new Pricing();

  int total(Order order) {
    Pricing local = new Pricing();
    return pricing.priceOf(order, pricing.rate(order)) + local.priceOf(order, local.rate(order)) + this.pricing.priceOf(order, this.pricing.rate(order));
  }
}
