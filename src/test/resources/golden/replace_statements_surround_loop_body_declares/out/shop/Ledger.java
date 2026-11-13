package shop;

import java.util.List;
import java.util.Map;
import shop.money.Money;

class Ledger {
  private List<Order> orders;
  private Map<String, List<Order>> byCustomer;
  private String[] names;
  private List raw;
  private Report report;

  Money totalFor(String customer) {
    Money total = Money.zero();
    for (Order order2 : orders) {
      Order order = orders.get(0);
      total = total.plus(order.amount());
      report.add(customer, 1);
    }
    return total;
  }
}
