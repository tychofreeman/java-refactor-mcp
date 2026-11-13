package shop;

class Discount extends Pricing {
  @Override
  int priceOf(Order order) {
    return 0;
  }
}
