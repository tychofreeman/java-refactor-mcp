package shop;

import java.util.ArrayList;
import java.util.List;
import shop.money.Money;

class Checkout {
  private Pricing pricing;

  Money priceOf(Order order, String customer) {
    Money price = Money.zero();
    return price;
  }

  List<Order> open() {
    return new ArrayList<>();
  }
}
