package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class QualifiedTest {

  @Test
  void aBareNameHasNoType() {
    Qualified q = Qualified.parse("total");
    assertNull(q.type());
    assertEquals("total", q.name());
    assertNull(q.params());
  }

  @Test
  void aNestedTypeIsEverythingBeforeTheLastDot() {
    Qualified q = Qualified.parse("Outer.Inner.add");
    assertEquals("Outer.Inner", q.type());
    assertEquals("add", q.member());
  }

  @Test
  void dotsInParameterTypesStayInTheMember() {
    Qualified q = Qualified.parse("Sub.m(Map.Entry, java.util.List<String>)");
    assertEquals("Sub", q.type());
    assertEquals("m(Map.Entry, java.util.List<String>)", q.member());
    assertEquals("m", q.name());
    assertEquals(List.of("Map.Entry", "java.util.List<String>"), q.params());
  }

  @Test
  void commasInsideAngleBracketsDoNotSplitParameters() {
    Qualified q = Qualified.parse("Outer.Cache.put(Map<String, List<Integer>>, int)");
    assertEquals("Outer.Cache", q.type());
    assertEquals(List.of("Map<String,List<Integer>>", "int"), q.params());
  }

  @Test
  void varargsAndArraysKeepTheirMarks() {
    assertEquals(
        List.of("String[]", "int..."), Qualified.parse("Log.add(String[], int...)").params());
  }

  @Test
  void anEmptyListIsNoParameters() {
    assertEquals(List.of(), Qualified.parse("Cart.clear()").params());
  }

  @Test
  void anUnclosedListIsRefused() {
    Refusal r = assertThrows(Refusal.class, () -> Qualified.parse("Cart.add(int"));
    assertEquals("name Cart.add(int has an unclosed parenthesis.", r.getMessage());
  }

  @Test
  void ofRefusesANameWithoutAType() {
    Refusal r = assertThrows(Refusal.class, () -> Qualified.of("total", "Type.method"));
    assertEquals("name is Type.method, not total.", r.getMessage());
  }
}
