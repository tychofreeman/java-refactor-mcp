package shop;

import shop.money.Money;

public class Pricing {
  Money priceOf(Order order, String customer) {
    return Money.zero();
  }

  Money quote(Order order) {
    throw new UnsupportedOperationException("not implemented");
  }
}
