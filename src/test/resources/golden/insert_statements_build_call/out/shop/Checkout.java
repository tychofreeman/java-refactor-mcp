package shop;

class Checkout {
  private final Pricing pricing;
  private final Audit audit;

  Checkout(Pricing pricing, Audit audit) {
    this.pricing = pricing;
    this.audit = audit;
  }

  void pay(Order order, String customer) {
    audit.record(order);
    audit.record(order);
  }
}
