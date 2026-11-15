package com.example;

import java.util.ArrayList;
import java.util.List;

/** Holds the lines, each a {@link Shop.Line}. */
public class Shop {
  private final List<Shop.Line> lines = new ArrayList<>();

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

  private static String label(String name) {
    return name.toUpperCase();
  }

  /** One line of the order. */
  public static final class Line {
    public static final Line EMPTY = new Line("", 0);
    public final String name;
    private final int price;

    Line(String name, int price) {
      this.name = label(name);
      this.price = price;
    }

    List<String> parts() {
      return List.of(name);
    }
  }
}
