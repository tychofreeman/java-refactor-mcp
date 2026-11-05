package warehouse.alerts;

import java.util.List;
import java.util.Locale;

/** The sensors of the warehouse, each raising an alert when it reads too high. */
public final class Sensors {

  private Sensors() {}

  /** A temperature sensor. */
  public record Temp(String room, double limit) implements Sensor {
    @Override
    public void probe(double v, List<String> alerts) {
      if (v >= limit) {
        alerts.add(String.format(Locale.ROOT, "%s: temp %.1f over %.1f", room, v, limit));
      }
    }
  }

  /** A humidity sensor. */
  public record Humidity(String room, double limit) implements Sensor {
    @Override
    public void probe(double v, List<String> alerts) {
      if (v >= limit) {
        alerts.add(String.format(Locale.ROOT, "%s: humidity %.1f over %.1f", room, v, limit));
      }
    }
  }

  /** A smoke sensor. */
  public record Smoke(String room, double limit) implements Sensor {
    @Override
    public void probe(double v, List<String> alerts) {
      if (v >= limit) {
        alerts.add(String.format(Locale.ROOT, "%s: smoke %.1f over %.1f", room, v, limit));
      }
    }
  }

  /** A sensor of the weight on a rack. */
  public record Load(String room, double limit) implements Sensor {
    @Override
    public void probe(double v, List<String> alerts) {
      if (v >= limit) {
        alerts.add(String.format(Locale.ROOT, "%s: load %.1f over %.1f", room, v, limit));
      }
    }
  }

  /** A sound level sensor. */
  public record Noise(String room, double limit) implements Sensor {
    @Override
    public void probe(double v, List<String> alerts) {
      if (v >= limit) {
        alerts.add(String.format(Locale.ROOT, "%s: noise %.1f over %.1f", room, v, limit));
      }
    }
  }

  /** A dust sensor. */
  public record Dust(String room, double limit) implements Sensor {
    @Override
    public void probe(double v, List<String> alerts) {
      if (v >= limit) {
        alerts.add(String.format(Locale.ROOT, "%s: dust %.1f over %.1f", room, v, limit));
      }
    }
  }

  /** A carbon monoxide sensor. */
  public record Gas(String room, double limit) implements Sensor {
    @Override
    public void probe(double v, List<String> alerts) {
      if (v > limit) {
        alerts.add(String.format(Locale.ROOT, "%s: gas %.1f over %.1f", room, v, limit));
      }
    }
  }

  /** A sensor on a conveyor motor. */
  public record Vibration(String room, double limit) implements Sensor {
    @Override
    public void probe(double v, List<String> alerts) {
      if (v >= limit) {
        alerts.add(String.format(Locale.ROOT, "%s: vibration %.1f over %.1f", room, v, limit));
      }
    }
  }

  /** A light level sensor. */
  public record Light(String room, double limit) implements Sensor {
    @Override
    public void probe(double v, List<String> alerts) {
      if (v >= limit) {
        alerts.add(String.format(Locale.ROOT, "%s: light %.1f over %.1f", room, v, limit));
      }
    }
  }

  /** A flood sensor. */
  public record Flood(String room, double limit) implements Sensor {
    @Override
    public void probe(double v, List<String> alerts) {
      if (v >= limit) {
        alerts.add(String.format(Locale.ROOT, "%s: flood %.1f over %.1f", room, v, limit));
      }
    }
  }
}
