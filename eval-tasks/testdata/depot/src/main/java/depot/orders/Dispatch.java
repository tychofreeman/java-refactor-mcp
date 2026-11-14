package depot.orders;

import java.util.ArrayList;
import java.util.List;

/** Decisions made when orders leave the depot. */
public final class Dispatch {
  /** The ids of the orders whose shipping costs more than the limit, in the order given. */
  public List<String> costly(List<Order> orders, int limitCents) {
    List<String> ids = new ArrayList<>();
    for (Order order : orders) {
      if (Shipping.cost(order, order.customer().region()) > limitCents) {
        ids.add(order.id());
      }
    }
    return ids;
  }
}
