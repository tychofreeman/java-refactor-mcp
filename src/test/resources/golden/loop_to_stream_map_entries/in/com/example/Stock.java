package com.example;

import java.util.Map;

public class Stock {
  void print(Map<String, Integer> stock) {
    for (Map.Entry<String, Integer> e : stock.entrySet()) {
      System.out.println(e.getKey() + "=" + e.getValue());
    }
  }
}
