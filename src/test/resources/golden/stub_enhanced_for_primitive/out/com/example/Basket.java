package com.example;

import java.util.List;

class Basket {
  int total() {
    int sum = 0;
    for (int n : items()) {
      sum += n;
    }
    return sum;
  }

  List<Integer> items() {
    throw new UnsupportedOperationException("not implemented");
  }
}
