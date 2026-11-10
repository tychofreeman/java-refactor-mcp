package com.example;

import java.util.List;

public class Main {
  boolean any(List<String> xs) {
    boolean any = false;
    for (String x : xs) {
      any = true;
      break;
    }
    return any;
  }
}
