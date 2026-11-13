package shop;

import java.util.List;
import java.util.Objects;
import shop.money.Money;

class Ledger {
  private List<Order> orders;

  private Pricing pricing;

  Ledger(List<Order> orders) {
    this.orders = orders;
  }

  Money totalFor(String customer) {
    Objects.requireNonNull(customer);
    Money total = Money.zero();
    for (Order order : orders) {
      if (!order.customer().equals(customer)) {
        continue;
      }
      Money price = pricing.priceOf(order, customer);
      total = total.plus(price);
    }
    return total;
  }
}
