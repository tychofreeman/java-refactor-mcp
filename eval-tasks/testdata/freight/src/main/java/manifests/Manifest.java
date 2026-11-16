package manifests;

import freight.Shipment;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/** The shipments of one day, as the depots and yards read them. */
public final class Manifest {
  private final List<Shipment> shipments = new ArrayList<>();

  public void add(Shipment s) {
    shipments.add(s);
  }

  /** A shipment's line: "id depot/yard", a dash for a pickup it lacks. */
  public static String line(Shipment s) {
    return s.id + " " + orDash(s.road) + "/" + orDash(s.rail);
  }

  /** The line of each shipment, in the order added. */
  public List<String> lines() {
    List<String> out = new ArrayList<>();
    for (Shipment s : shipments) {
      out.add(line(s));
    }
    return out;
  }

  /** The shipments that leave from a road depot. */
  public List<Shipment> fromDepot(String depot) {
    List<Shipment> out = new ArrayList<>();
    for (Shipment s : shipments) {
      if (depot.equals(s.road)) {
        out.add(s);
      }
    }
    return out;
  }

  /** The shipments that leave from a rail yard. */
  public List<Shipment> fromYard(String yard) {
    List<Shipment> out = new ArrayList<>();
    for (Shipment s : shipments) {
      if (yard.equals(s.rail)) {
        out.add(s);
      }
    }
    return out;
  }

  /** Moves every shipment of one depot to another, and returns how many moved. */
  public int reroute(String from, String to) {
    int moved = 0;
    for (Shipment s : shipments) {
      if (from.equals(s.road)) {
        s.road = to;
        moved++;
      }
    }
    return moved;
  }

  /** Moves every shipment of one yard to another, and returns how many moved. */
  public int rerail(String from, String to) {
    int moved = 0;
    for (Shipment s : shipments) {
      if (from.equals(s.rail)) {
        s.rail = to;
        moved++;
      }
    }
    return moved;
  }

  /** The ids of the shipments that go by road and not by rail. */
  public List<String> roadOnly() {
    List<String> out = new ArrayList<>();
    for (Shipment s : shipments) {
      if (s.road != null && s.rail == null) {
        out.add(s.id);
      }
    }
    return out;
  }

  /** Every depot a shipment leaves from, sorted. */
  public SortedSet<String> depots() {
    SortedSet<String> out = new TreeSet<>();
    for (Shipment s : shipments) {
      if (s.road != null) {
        out.add(s.road);
      }
    }
    return out;
  }

  private static String orDash(String code) {
    return code == null ? "-" : code;
  }
}