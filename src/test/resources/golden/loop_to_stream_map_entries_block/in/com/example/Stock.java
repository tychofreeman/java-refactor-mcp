package com.example;

import java.util.List;
import java.util.Map;

public class Stock {
  void low(Map<String, Integer> stock, List<String> out) {
    for (Map.Entry<String, Integer> entry : stock.entrySet()) {
      if (entry.getValue() < 3) {
        out.add(entry.getKey());
      }
      System.out.println(entry.getKey());
    }
  }
}
