package com.example;

import java.util.Map;

public class Stock {
  void print(Map<String, Integer> stock) {
    stock.forEach((key, value) -> System.out.println(key + "=" + value));
  }
}
