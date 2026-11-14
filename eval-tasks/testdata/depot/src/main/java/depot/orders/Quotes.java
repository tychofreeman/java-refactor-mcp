package depot.orders;

/** Prices quoted to customers. */
public final class Quotes {
  /** The order's id and its price with shipping, as "O-1: 47.99". */
  public String quote(Order order) {
    int shipping = Shipping.cost(order, order.customer().region());
    return order.id() + ": " + euros(order.totalCents() + shipping);
  }

  static String euros(int cents) {
    return String.format("%d.%02d", cents / 100, cents % 100);
  }
}
