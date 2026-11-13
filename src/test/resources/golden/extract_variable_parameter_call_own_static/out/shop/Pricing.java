package shop;

class Pricing {
  int priceOf(int qty, int scaled) {
    return scaled + 1;
  }

  static int scale(int q) {
    return q * 3;
  }

  int twice() {
    return priceOf(2, scale(2)) * 2;
  }

  static class Promo {
    int deal() {
      return new Pricing().priceOf(4, scale(4));
    }
  }
}
