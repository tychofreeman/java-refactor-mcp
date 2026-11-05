package warehouse.gates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class YardTest {

  @Test
  void admit() {
    Yard y = new Yard(List.of("d1"));
    y.paid.add("t1");
    assertTrue(y.admit("t1"));
    assertEquals("t1", y.taken.get("d1"));
  }

  @Test
  void passAndFits() {
    Yard y = new Yard(List.of());
    assertTrue(y.pass("t1", true));
    assertFalse(y.pass("t2", false));
    assertTrue(Yard.fits(24));
    assertFalse(Yard.fits(0));
  }

  @Test
  void onTime() {
    assertTrue(new Yard(List.of(), 7).onTime());
    assertFalse(new Yard(List.of(), 5).onTime());
  }
}
