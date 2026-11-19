package com.example;

import java.util.List;
import java.util.Set;

public class Sizes {
  static int twice(List<String> items) {
    return (items.size() + 1) * 2;
  }

  static int both(List<String> a, List<Integer> b, Set<String> c) {
    int n = a.size() + 1;
    return n + (b.size() + 1) + c.size();
  }
}
