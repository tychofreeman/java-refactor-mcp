package com.example;

import java.util.ArrayList;
import java.util.List;

class Shop {
  int f(List<String> xs) {
    List<String> o = new ArrayList<>();
    for (String s : xs) {
      if (s.startsWith(
          "a")) {
        o.add(s);
      }
    }
    return o.size();
  }
}
