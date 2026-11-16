package freight;

import java.util.ArrayList;
import java.util.List;

/** Ships by plane. */
public class Plane implements Carrier {
  /** What was shipped, "airport: ref". */
  public final List<String> shipped = new ArrayList<>();

  @Override
  public void ship(Shipment s, Order o) {
    shipped.add(s.air + ": " + o.ref());
  }

  @Override
  public String name() {
    return "plane";
  }
}