package warehouse.alerts;

import java.util.List;

/** A sensor in the warehouse. */
public interface Sensor {

  /** Adds the alert for reading v, if there is one, to alerts. */
  void probe(double v, List<String> alerts);
}
