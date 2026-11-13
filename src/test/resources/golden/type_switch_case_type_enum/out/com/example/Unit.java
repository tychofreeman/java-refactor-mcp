package com.example;

public enum Unit implements Shape {
  A, B;

  @Override
  public int sides() {
    return 4;
  }
}
