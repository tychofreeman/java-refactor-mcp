package com.example;

class Picker {
  static <T> T pick(Object a, T b) {
    return b;
  }

  @SuppressWarnings("unchecked")
  static <U> U use(Object x) {
    return pick(x, (U) x);
  }
}
