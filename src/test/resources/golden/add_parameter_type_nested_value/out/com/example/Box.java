package com.example;

public class Box<T> {
  public static class Entry {}

  static Box<Box.Entry> empty() {
    return new Box<>();
  }

  class Inner {
    Box<T> outer() {
      return Box.this;
    }
  }

  static class Nested {
    Box<Box.Entry> made = new Box<>();
  }
}
