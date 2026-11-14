package com.example;

import java.util.List;

class Shop {
  int f(List<String> xs) {
    List<String> o = xs.stream()
        .filter(s -> s.startsWith(
          "a"))
        .toList();
    return o.size();
  }
}
