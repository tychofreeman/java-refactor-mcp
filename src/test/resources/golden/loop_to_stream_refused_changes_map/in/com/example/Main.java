package com.example;

import java.util.Map;

public class Main {
  void drop(Map<String, Integer> stock) {
    for (Map.Entry<String, Integer> e : stock.entrySet()) {
      stock.remove(e.getKey());
    }
  }
}
