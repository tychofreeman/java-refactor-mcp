package com.example;

import java.util.List;

public class Order {
  private final String id;

  Order(String id) {
    this.id = id;
  }

  String describe(Line line) {
    return id + ": " + line.name;
  }

  /** One line of the order. */
  public static final class Line {
    public static final Line EMPTY = new Line("", 0);
    public final String name;
    final int price;

    Line(String name, int price) {
      this.name = Shop.label(name);
      this.price = price;
    }

    List<String> parts() {
      return List.of(name);
    }
  }
}
