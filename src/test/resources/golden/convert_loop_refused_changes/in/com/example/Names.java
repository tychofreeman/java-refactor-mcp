package com.example;

import java.util.List;

public class Names {
  void grow(List<String> names) {
    for (int i = 0; i < names.size(); i++) {
      if (names.get(i).isEmpty()) {
        names.add("x");
      }
    }
  }
}
