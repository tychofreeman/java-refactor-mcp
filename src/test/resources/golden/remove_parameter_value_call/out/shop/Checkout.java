package shop;

class Checkout {
  record Cart(Order order) {}

  private final Pricing pricing = new Pricing();

  int total(Order order, Cart cart) {
    return pricing.priceOf(order)
        + pricing.priceOf(cart.order());
  }
}
