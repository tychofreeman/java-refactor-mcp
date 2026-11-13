package shop;

import java.util.List;
import java.util.Objects;
import shop.money.Money;

class Ledger {
  private List<Order> orders;
  private int count;

  Money totalFor(String customer) {
    Money total = Money.zero();
    for (Order order : orders) {
      total = total.plus(order.amount());
    }
    return total;
  }

  void add(Order order, int quantity) {
    Objects.requireNonNull(order);
    orders.add(order);
  }

  void load(String path) {
    count++;
  }
}
