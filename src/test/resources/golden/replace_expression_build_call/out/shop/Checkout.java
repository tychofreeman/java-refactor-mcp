package shop;

import java.util.List;
import shop.money.Money;

class Checkout {
  private Pricing pricing;

  Money priceOf(Order order, String customer) {
    Money price = Money.zero();
    return pricing.apply(price, customer);
  }

  List<Order> open() {
    return null;
  }
}
