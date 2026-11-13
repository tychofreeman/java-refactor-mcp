package com.example;

class Range<T extends Comparable<T>> {
  private final T lo;
  private final T hi;

  Range(T lo, T hi) {
    this.lo = lo;
    this.hi = hi;
  }

  boolean contains(T x) {
    return lo.compareTo(x) <= 0 && x.compareTo(hi) <= 0;
  }

  static boolean digit(int d) {
    Range<Integer> r = new Range<>(0, 9);
    return r.contains(d);
  }
}
