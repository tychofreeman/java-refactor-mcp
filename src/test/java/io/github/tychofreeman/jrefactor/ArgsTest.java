package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArgsTest {

  private static Args args(Object... keysAndValues) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      values.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return new Args("move", values);
  }

  @Test
  void routedKeepsOnlyTheTargetsArgumentsUnderItsName() {
    Args routed =
        args("file", "A.java", "name", "A.m", "to", "B", "copy", false, "package", null)
            .routed("move_member", Set.of("file", "name", "to", "package", "imports"));
    assertEquals("move_member", routed.tool());
    assertEquals("B", routed.string("to"));
    assertFalse(routed.has("copy"));
    assertFalse(routed.has("package"));
    Refusal refusal = assertThrows(Refusal.class, () -> routed.required("imports"));
    assertEquals("move_member needs imports. Pass imports.", refusal.getMessage());
  }

  @Test
  void valuesAreTheArgumentsAsSent() {
    assertEquals(Map.of("over", "items"), args("over", "items").values());
  }

  @Test
  void requiredRefusesAnEmptyValueInTheArgumentsWords() {
    Args sent =
        args("line", 3, "name", "")
            .routed("invert_if", List.of(Arg.FILE, Arg.string("name", "The if's name").required()));
    Refusal refusal = assertThrows(Refusal.class, () -> sent.required("name"));
    assertEquals("invert_if needs name: The if's name. Pass name.", refusal.getMessage());
    refusal = assertThrows(Refusal.class, () -> sent.required("file"));
    assertEquals("invert_if needs file: Java file path. Pass file.", refusal.getMessage());
  }
}
