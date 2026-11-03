package com.geo;

public class Square implements Shape {
  private final double side;

  public Square(double side) {
    this.side = side;
  }

  @Override
  public double surface() {
    return side * side;
  }
}
