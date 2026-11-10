package com.example;

import java.util.List;
import java.util.Map;

public class Stock {
  void low(Map<String, Integer> stock, List<String> out) {
    stock.forEach((key, value) -> {
      if (value < 3) {
        out.add(key);
      }
      System.out.println(key);
    });
  }
}
