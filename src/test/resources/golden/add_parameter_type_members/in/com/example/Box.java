package com.example;

import java.util.List;

public class Box {
  private final String value;

  public Box(String value) {
    this.value = value;
  }

  public String get() {
    return value;
  }

  public Box copy() {
    return new Box(value);
  }

  public boolean same(Box other, List<String> seen) {
    return other.value.equals(value) && !seen.isEmpty();
  }

  public int size() {
    return 1;
  }
}
