package freight;

import java.util.ArrayList;
import java.util.List;

/** Ships orders through its carriers. */
public final class Dispatcher {
  private final List<Carrier> carriers;

  public Dispatcher(List<Carrier> carriers) {
    this.carriers = List.copyOf(carriers);
  }

  /**
   * Ships o to each shipment through every carrier, in the order given, and returns a summary line
   * for each: "sender: name,name".
   */
  public List<String> dispatch(List<Shipment> shipments, Order o) {
    List<String> out = new ArrayList<>();
    for (Shipment s : shipments) {
      List<String> names = new ArrayList<>();
      for (Carrier c : carriers) {
        c.ship(s, o);
        names.add(c.name());
      }
      out.add(s.sender + ": " + String.join(",", names));
    }
    return out;
  }
}