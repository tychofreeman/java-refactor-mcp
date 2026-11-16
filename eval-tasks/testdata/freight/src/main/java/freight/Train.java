package freight;

import java.util.ArrayList;
import java.util.List;

/** Ships by train. */
public class Train implements Carrier {
  /** What was shipped, "yard: ref weight". */
  public final List<String> shipped = new ArrayList<>();

  @Override
  public void ship(Shipment s, Order o) {
    shipped.add(s.rail + ": " + o.ref() + " " + o.weight() + "kg");
  }

  @Override
  public String name() {
    return "train";
  }

  /** Whether the yard of s takes an order of o's weight: yards marked H take any. */
  public boolean takes(Shipment s, Order o) {
    return o.weight() <= 500 || s.rail.endsWith("H");
  }
}