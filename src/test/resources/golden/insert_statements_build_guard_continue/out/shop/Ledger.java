package shop;

import java.util.List;
import shop.money.Money;

class Ledger {
  private List<Order> orders;
  private int count;

  Money totalFor(String customer) {
    Money total = Money.zero();
    for (Order order : orders) {
      if (!order.customer().equals(customer)) {
        continue;
      }
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
