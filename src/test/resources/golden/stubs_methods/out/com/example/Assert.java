package com.example;

final class Assert {
  static void assertEquals(Object expected, Object actual) {
    if (!expected.equals(actual)) {
      throw new AssertionError(expected + " != " + actual);
    }
  }
}
