package shop;

import shop.money.Money;

public class Pricing {
  Money priceOf(Order order, String customer) {
    return order.amount();
  }
}
