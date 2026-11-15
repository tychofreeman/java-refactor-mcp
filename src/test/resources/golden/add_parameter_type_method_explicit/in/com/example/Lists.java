package com.example;

import java.util.ArrayList;
import java.util.List;

class Lists {
  static String first(List<String> xs) {
    return xs.get(0);
  }

  Object any() {
    return first(new ArrayList<>());
  }
}
