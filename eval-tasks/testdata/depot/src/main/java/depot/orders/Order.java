package depot.orders;

import java.util.List;

/** A customer's order. */
public final class Order {
  private final String id;
  private final Customer customer;
  private final List<Line> lines;

  public Order(String id, Customer customer, List<Line> lines) {
    this.id = id;
    this.customer = customer;
    this.lines = List.copyOf(lines);
  }

  public String id() {
    return id;
  }

  public Customer customer() {
    return customer;
  }

  public List<Line> lines() {
    return lines;
  }

  /** The price of every line, in cents, without shipping. */
  public int totalCents() {
    int cents = 0;
    for (Line line : lines) {
      cents += line.cents();
    }
    return cents;
  }

  public int weightGrams() {
    int grams = 0;
    for (Line line : lines) {
      grams += line.grams();
    }
    return grams;
  }
}
