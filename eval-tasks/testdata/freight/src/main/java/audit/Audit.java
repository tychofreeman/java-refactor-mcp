package audit;

import freight.Shipment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Checks shipments before they are dispatched. */
public final class Audit {
  private Audit() {}

  /** What is wrong with s, one line a problem; empty when nothing is. */
  public static List<String> problems(Shipment s) {
    List<String> out = new ArrayList<>();
    if (s.road == null && s.rail == null && s.air == null) {
      out.add(s.id + ": no pickup");
    }
    if (s.road != null && !s.road.startsWith("RD-")) {
      out.add(s.id + ": road code " + s.road);
    }
    if (s.rail != null && !s.rail.startsWith("YARD")) {
      out.add(s.id + ": rail code " + s.rail);
    }
    return out;
  }

  /** How many shipments leave from each depot, by depot. */
  public static Map<String, Integer> countByDepot(List<Shipment> shipments) {
    Map<String, Integer> out = new TreeMap<>();
    for (Shipment s : shipments) {
      if (s.road != null) {
        out.merge(s.road, 1, Integer::sum);
      }
    }
    return out;
  }

  /** A shipment as the audit log writes it. */
  public static String describe(Shipment s) {
    return String.format("%s road=%s rail=%s air=%s", s.id, s.road, s.rail, s.air);
  }
}