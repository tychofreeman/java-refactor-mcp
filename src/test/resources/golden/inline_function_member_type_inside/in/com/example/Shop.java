package com.example;

public class Shop {
  public static class Stack {}

  public static Object fresh() {
    return new Stack();
  }

  Object take() {
    return fresh();
  }
}
