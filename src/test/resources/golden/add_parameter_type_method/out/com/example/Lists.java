package com.example;

import java.util.List;

class Lists {
  static <T> T first(List<T> xs) {
    return xs.get(0);
  }
}
