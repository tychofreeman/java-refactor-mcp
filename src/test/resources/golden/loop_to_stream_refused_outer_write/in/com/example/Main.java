package com.example;

import java.util.List;

public class Main {
  String last(List<String> xs) {
    String last = null;
    for (String x : xs) {
      last = x;
    }
    return last;
  }
}
