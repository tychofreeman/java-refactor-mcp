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
    audit.record(order);
  }

  Money total(Order order, String customer) {
    Money total = Money.zero();
    Money price = pricing.priceOf(order, customer);
    // the price counts once
    total = total.plus(price);
    audit.record(order);
    return total.plus(price);
  }
}
