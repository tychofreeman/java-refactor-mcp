package shop;

class Order {
  private final String customer;
  private final int amount;
  private int asked;

  Order(String customer, int amount) {
    this.customer = customer;
    this.amount = amount;
  }

  String customer() {
    asked++;
    return customer;
  }

  int amount() {
    return amount;
  }
}
