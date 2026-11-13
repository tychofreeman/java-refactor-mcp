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
    for (int i = 0; i < names.length; i++) {
      for (int j = 0; j < orders.size(); j++) {
      }
    }
    for (int i = 0; i < names.length; i++) {
    }
    return total;
  }
}
