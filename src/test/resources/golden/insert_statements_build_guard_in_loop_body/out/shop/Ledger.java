package shop;

import java.util.List;
import shop.money.Money;

class Ledger {
  private List<Order> orders;
  private int count;

  Money totalFor(String customer) {
    Money total = Money.zero();
    for (Order order : orders) {
      total = total.plus(order.amount());
    }
    for (Order o : orders) {
      if (o == null) {
        break;
      }
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
