package shop;

class Checkout {
  record Cart(Order order) {}

  private final Pricing pricing = new Pricing();

  int total(Order order, Cart cart) {
    return pricing.priceOf(order, order.customer()) + pricing.priceOf(cart.order(), (cart.order()).customer());
  }
}
