package com.example;

import java.util.ArrayList;
import java.util.List;

/** Holds the lines, each a {@link Order.Line}. */
public class Shop {
  private final List<Order.Line> lines = new ArrayList<>();

  void add(String name, int price) {
    lines.add(new Order.Line(name, price));
  }

  int total() {
    int sum = 0;
    for (Order.Line l : lines) {
      sum += l.price;
    }
    return sum;
  }

  static String label(String name) {
    return name.toUpperCase();
  }
}
