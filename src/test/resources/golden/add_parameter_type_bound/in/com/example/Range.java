package com.example;

class Range {
  private final Integer lo;
  private final Integer hi;

  Range(Integer lo, Integer hi) {
    this.lo = lo;
    this.hi = hi;
  }

  boolean contains(Integer x) {
    return lo.compareTo(x) <= 0 && x.compareTo(hi) <= 0;
  }

  static boolean digit(int d) {
    Range r = new Range(0, 9);
    return r.contains(d);
  }
}
