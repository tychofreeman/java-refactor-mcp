package com.example;

import java.util.List;

class Basket {
  int count() {
    int n = 0;
    for (String item : items()) {
      n++;
    }
    return n;
  }

  List<String> items() {
    throw new UnsupportedOperationException("not implemented");
  }
}
