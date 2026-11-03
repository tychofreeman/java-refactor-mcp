package com.example;

import java.util.ArrayList;
import java.util.List;

public class Shop {
  private final List<Line> lines = new ArrayList<>();

  void add(String name, int price) {
    lines.add(new Line(name, price));
  }

  int total() {
    int sum = 0;
    for (Line l : lines) {
      sum += l.price;
    }
    return sum;
  }

  static String label(String name) {
    return name.toUpperCase();
  }
}
