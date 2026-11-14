package depot.orders;

/** Someone who orders from the depot. */
public final class Customer {
  private final String name;
  private final Account account;
  private final Region region;

  public Customer(String name, Account account, Region region) {
    this.name = name;
    this.account = account;
    this.region = region;
  }

  public String name() {
    return name;
  }

  public Account account() {
    return account;
  }

  /** Where the customer's orders ship to. */
  public Region region() {
    return region;
  }
}
