package warehouse.pricing;

/** Prices storage and handling for customers. */
public final class Pricing {

  /** The share added to every cost. */
  static double margin = 0.25;

  private Pricing() {}

  /** A customer's price list. */
  public static final class Rate {
    final String customer;
    final double perDay;
    private final double markup; // a handling fee added to each order

    Rate(String customer, double perDay, double markup) {
      this.customer = customer;
      this.perDay = perDay;
      this.markup = markup;
    }

    /** Prices days of storage, with the margin, and adds the handling fee. */
    public double storage(int days) {
      return round(days * perDay * (1 + margin)) + markup;
    }
  }

  /** A price list with a handling fee. */
  public static Rate newRate(String customer, double perDay, double fee) {
    return new Rate(customer, perDay, fee);
  }

  /** Adds the margin to cost. */
  public static double price(double cost) {
    return round(cost * (1 + margin));
  }

  /** Prices cost and adds a handling fee. */
  public static double quote(double cost, double markup) {
    return price(cost) + markup;
  }

  /** Prices n units at cost each, with the margin on the whole order, and adds a fee once. */
  public static double bulk(double cost, int n, double markup) {
    double total = n * cost;
    return round(total * (1 + margin)) + markup;
  }

  /** Prices cost with the margin reduced by off, and never below cost. */
  public static double reduced(double cost, double off) {
    double markup = margin - off;
    if (markup < 0) {
      markup = 0;
    }
    return round(cost * (1 + markup));
  }

  /** The least price for cost: the margin on cost, at least one unit. */
  public static double floor(double cost) {
    return Math.max(1, round(cost * margin));
  }

  static double round(double x) {
    return Math.round(x * 100) / 100.0;
  }
}
