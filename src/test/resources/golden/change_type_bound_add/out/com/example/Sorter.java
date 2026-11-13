package com.example;

import java.util.Comparator;
import java.util.List;

class Sorter {
  static <T extends Comparable<T>> T max(List<T> xs, Comparator<T> c) {
    T best = xs.get(0);
    for (T x : xs) {
      if (c.compare(x, best) > 0) {
        best = x;
      }
    }
    return best;
  }

  static int top(List<Integer> xs) {
    return max(xs, Comparator.naturalOrder());
  }
}
