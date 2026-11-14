package shop;

import java.util.List;
import shop.money.Money;

class Ledger {
  private List<Order> orders;

  Ledger(List<Order> orders) {
    this.orders = orders;
  }

  Money totalFor(String customer) {
    throw new UnsupportedOperationException("not implemented");
  }
}
