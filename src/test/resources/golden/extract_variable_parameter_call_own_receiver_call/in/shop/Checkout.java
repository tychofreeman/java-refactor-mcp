package shop;

class Checkout {
  Pricing pricing() {
    return new Pricing();
  }

  int total(Order order) {
    return pricing().priceOf(order);
  }
}
