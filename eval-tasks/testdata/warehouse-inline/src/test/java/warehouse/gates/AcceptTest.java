package warehouse.gates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AcceptTest {

  @Test
  void helpersGone() {
    for (Method m : Yard.class.getDeclaredMethods()) {
      assertFalse(Set.of("both", "either", "within").contains(m.getName()), m.getName());
    }
  }

  @Test
  void admitChargesWithoutDoor() {
    Yard y = new Yard(List.of());
    y.paid.add("t1");
    assertFalse(y.admit("t1"), "admitted without a door");
    assertEquals(List.of("t1"), y.charged);
  }

  @Test
  void expressCharges() {
    Yard y = new Yard(List.of());
    y.paid.add("t1");
    assertTrue(y.express("t1"));
    assertEquals(List.of("t1"), y.charged);
  }

  @Test
  void onTimeTakesOneSlot() {
    Yard y = new Yard(List.of(), 7, 12, 9);
    assertTrue(y.onTime(), "7");
    assertFalse(y.onTime(), "12");
    assertTrue(y.onTime(), "9");
  }
}
