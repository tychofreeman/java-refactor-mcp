package com.example;

import java.util.List;

/** One line of the order. */
public final class Line {
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
