package com.example;

public class Shop {
  public static class Stack {}

  static Object fresh() {
    return new Stack();
  }

  Object begin() {
    return fresh();
  }
}
