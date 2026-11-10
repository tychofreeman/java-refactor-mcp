package com.example;

import java.util.List;

public class Main {
  double total(List<Double> prices) {
    double total = 0;
    for (Double price : prices) {
      total += price;
    }
    return total;
  }
}
