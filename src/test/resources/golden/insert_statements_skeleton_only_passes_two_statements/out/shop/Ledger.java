package shop;

import java.util.List;
import shop.money.Money;

class Ledger {
  private List<Order> orders;
  private int count;

  Money totalFor(String customer) {
    Money total = Money.zero();
    total = total.plus(Money.zero());
    count++;
    for (Order order : orders) {
      total = total.plus(order.amount());
    }
    return total;
  }

  void add(Order order, int quantity) {
    orders.add(order);
  }

  void load(String path) {
    count++;
  }
}
