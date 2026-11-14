package com.example;

final class Pure {
  static int size(String s) {
    return s.length();
  }

  static boolean pure(Object e) {
    return e instanceof String || allPure(e);
  }

  static boolean allPure(Object e) {
    return e instanceof Iterable<?> i && i.iterator().hasNext() && pure(i.iterator().next());
  }
}
