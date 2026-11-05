package warehouse.alerts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AcceptTest {

  /** A probe method whose body is one call: the callee's name is group 1. */
  private static final Pattern ONE_CALL =
      Pattern.compile(
          "void\\s+probe\\s*\\([^)]*\\)\\s*\\{\\s*([\\w.]+)\\s*\\([^;{}]*\\)\\s*;\\s*\\}");

  @Test
  void oneMethod() throws IOException {
    Map<String, Integer> callees = new HashMap<>();
    try (Stream<Path> files = Files.list(Path.of("src/main/java/warehouse/alerts"))) {
      for (Path f : files.toList()) {
        Matcher m = ONE_CALL.matcher(Files.readString(f));
        while (m.find()) {
          callees.merge(m.group(1), 1, Integer::sum);
        }
      }
    }
    assertTrue(
        callees.values().stream().anyMatch(n -> n >= 9),
        "probe methods that are one call, by callee: " + callees);
  }

  @Test
  void atLimit() {
    List<String> alerts = new ArrayList<>();
    List<Sensor> sensors =
        List.of(
            new Sensors.Temp("r", 1),
            new Sensors.Humidity("r", 1),
            new Sensors.Smoke("r", 1),
            new Sensors.Load("r", 1),
            new Sensors.Noise("r", 1),
            new Sensors.Dust("r", 1),
            new Sensors.Gas("r", 1),
            new Sensors.Vibration("r", 1),
            new Sensors.Light("r", 1),
            new Sensors.Flood("r", 1));
    for (Sensor s : sensors) {
      s.probe(1, alerts);
    }
    assertEquals(
        List.of(
            "r: temp 1.0 over 1.0",
            "r: humidity 1.0 over 1.0",
            "r: smoke 1.0 over 1.0",
            "r: load 1.0 over 1.0",
            "r: noise 1.0 over 1.0",
            "r: dust 1.0 over 1.0",
            "r: vibration 1.0 over 1.0",
            "r: light 1.0 over 1.0",
            "r: flood 1.0 over 1.0"),
        alerts,
        "gas alerts only above its limit");
  }
}
