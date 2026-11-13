package com.example;

import java.util.List;

public class Chooser {
  public <T extends Comparable<T>> T pick(List<T> options) {
    T best = options.get(0);
    for (T option : options) {
      if (option.compareTo(best) > 0) {
        best = option;
      }
    }
    return best;
  }
}
