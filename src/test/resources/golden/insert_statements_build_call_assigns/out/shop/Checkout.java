package shop;

import shop.money.Money;

class Checkout {
  private final Pricing pricing;
  private final Audit audit;

  Checkout(Pricing pricing, Audit audit) {
    this.pricing = pricing;
    this.audit = audit;
  }

  void pay(Order order, String customer) {
    Money total = Money.zero();
    Money price = pricing.priceOf(order, customer);
    total = total.plus(price);
    audit.record(order);
  }
}
