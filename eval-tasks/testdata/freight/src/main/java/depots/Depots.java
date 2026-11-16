package depots;

import freight.Shipment;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** The road depot and rail yard of each region. */
public final class Depots {
  private final Map<String, String> depotByRegion;
  private final Map<String, String> yardByRegion;

  public Depots(Map<String, String> depotByRegion, Map<String, String> yardByRegion) {
    this.depotByRegion = Map.copyOf(depotByRegion);
    this.yardByRegion = Map.copyOf(yardByRegion);
  }

  /** Gives s the depot and the yard of region, each only where s has none. */
  public void assign(Shipment s, String region) {
    if (s.road == null) {
      s.road = depotByRegion.get(region);
    }
    if (s.rail == null) {
      s.rail = yardByRegion.get(region);
    }
  }

  /** Clears a depot or yard of s that no region has. */
  public void prune(Shipment s) {
    if (s.road != null && !depotByRegion.containsValue(s.road)) {
      s.road = null;
    }
    if (s.rail != null && !yardByRegion.containsValue(s.rail)) {
      s.rail = null;
    }
  }

  /** Exchanges where two shipments are picked up by road and by rail. */
  public static void swap(Shipment a, Shipment b) {
    String road = a.road;
    a.road = b.road;
    b.road = road;
    String rail = a.rail;
    a.rail = b.rail;
    b.rail = rail;
  }

  /** Whether two shipments leave from the same depot and the same yard. */
  public static boolean together(Shipment a, Shipment b) {
    return Objects.equals(a.road, b.road) && Objects.equals(a.rail, b.rail);
  }

  /** Writes the codes of s as the depots file has them: trimmed, in capitals. */
  public static void normalize(Shipment s) {
    if (s.road != null) {
      s.road = s.road.trim().toUpperCase(Locale.ROOT);
    }
    if (s.rail != null) {
      s.rail = s.rail.trim().toUpperCase(Locale.ROOT);
    }
  }
}