package warehouse.alerts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SensorsTest {

  @Test
  void probe() {
    List<String> alerts = new ArrayList<>();
    new Sensors.Temp("cold", 5).probe(7, alerts);
    new Sensors.Flood("basement", 1).probe(0.5, alerts);
    new Sensors.Gas("dock", 30).probe(31, alerts);
    assertEquals(List.of("cold: temp 7.0 over 5.0", "dock: gas 31.0 over 30.0"), alerts);
  }
}
