package com.example;

public class Box<T> {
  private final String value;

  public Box(String value) {
    this.value = value;
  }

  public String get() {
    return value;
  }
}
