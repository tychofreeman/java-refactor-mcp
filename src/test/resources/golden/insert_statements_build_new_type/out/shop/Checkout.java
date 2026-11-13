package shop;

import java.util.ArrayList;
import java.util.List;

class Checkout {
  private final Pricing pricing;
  private final Audit audit;

  Checkout(Pricing pricing, Audit audit) {
    this.pricing = pricing;
    this.audit = audit;
  }

  void pay(Order order, String customer) {
    audit.record(order);
    List<Order> open = new ArrayList<>();
    Receipt receipt = new Receipt(order);
  }
}
