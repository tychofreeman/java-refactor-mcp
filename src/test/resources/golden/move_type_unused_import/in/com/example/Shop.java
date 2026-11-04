package com.example;

import java.util.ArrayList;
import java.util.List;

public class Shop {
  private final List<Line> lines = new ArrayList<>();

  /** One line of the order. */
  public static final class Line {
    public final String name;

    Line(String name) {
      this.name = name;
    }
  }
}
