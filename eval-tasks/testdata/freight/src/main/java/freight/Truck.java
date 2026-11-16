package freight;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Ships by truck. */
public class Truck implements Carrier {
  /** What was shipped, "depot: ref". */
  public final List<String> shipped = new ArrayList<>();

  @Override
  public void ship(Shipment s, Order o) {
    shipped.add(last(s.road, 4) + ": " + o.ref());
  }

  @Override
  public String name() {
    return "truck";
  }

  /** What a truck's label for o says. */
  public String label(Shipment s, Order o) {
    return "truck " + last(s.road, 4) + " " + o.ref();
  }

  /** A depot code as manifests show it. */
  public String masked(String code) {
    return "****" + last(code, 4);
  }

  /** The last 4 characters of each shipment's depot. */
  public List<String> tails(List<Shipment> shipments) {
    List<String> out = new ArrayList<>();
    for (Shipment s : shipments) {
      out.add(last(s.road, 4));
    }
    return out;
  }

  /** A hint for a depot on file, from its number. */
  public String hint(int n) {
    return last(String.format("%08d", n), 4);
  }

  /** A shipment's short form for drivers. */
  public String shortForm(Shipment s) {
    return "T-" + last(s.id, 4);
  }

  /** The tracking suffix of o. */
  public String track(Order o) {
    return last(o.ref(), 4);
  }

  /** The sender's badge on the cab. */
  public String badge(Shipment s) {
    return last(s.sender, 4).toUpperCase(Locale.ROOT);
  }

  /** A trailer plate from its serial number. */
  public String plate(int n) {
    return "TR" + last(Integer.toString(n), 4);
  }

  /** Whether two shipments leave from the same depot, by the part a driver sees. */
  public boolean same(Shipment a, Shipment b) {
    return last(a.road, 4).equals(last(b.road, 4));
  }

  /** The visible depot code of each shipment with a depot. */
  public List<String> codes(List<Shipment> shipments) {
    List<String> out = new ArrayList<>();
    for (Shipment s : shipments) {
      if (s.road != null) {
        out.add(last(s.road, 4));
      }
    }
    return out;
  }

  private static String last(String s, int n) {
    if (s.length() <= n) {
      return s;
    }
    return s.substring(s.length() - n);
  }
}