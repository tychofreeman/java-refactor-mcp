package shop;

class Checkout {
  private final Pricing pricing = new Pricing();

  int total(int n) {
    return pricing.priceOf(n, Pricing.scale(n));
  }
}
