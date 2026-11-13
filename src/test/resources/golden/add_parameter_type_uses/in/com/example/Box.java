package com.example;

public class Box {
  static final Box EMPTY = new Box("");

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

  public boolean same(Box other) {
    return other.value.equals(value);
  }

  static Box of(String value) {
    Box made = new Box(value);
    return made;
  }
}
