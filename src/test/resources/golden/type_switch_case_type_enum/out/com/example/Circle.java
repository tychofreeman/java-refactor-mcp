package com.example;

public record Circle(double r) implements Shape {
  @Override
  public int sides() {
    return 0;
  }
}
