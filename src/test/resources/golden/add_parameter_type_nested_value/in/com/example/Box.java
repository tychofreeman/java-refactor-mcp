package com.example;

public class Box {
  public static class Entry {}

  static Box empty() {
    return new Box();
  }

  class Inner {
    Box outer() {
      return Box.this;
    }
  }

  static class Nested {
    Box made = new Box();
  }
}
