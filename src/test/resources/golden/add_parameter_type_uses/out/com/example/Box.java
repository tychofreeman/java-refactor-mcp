package com.example;

public class Box<T> {
  static final Box<String> EMPTY = new Box<>("");

  private final String value;

  public Box(String value) {
    this.value = value;
  }

  public String get() {
    return value;
  }

  public Box<T> copy() {
    return new Box<>(value);
  }

  public boolean same(Box<T> other) {
    return other.value.equals(value);
  }

  static Box<String> of(String value) {
    Box<String> made = new Box<>(value);
    return made;
  }
}
