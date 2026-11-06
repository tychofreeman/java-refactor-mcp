package com.example;

record Square(double side) implements Shape {
  @Override
  public double area() {
    return side() * side();
  }
}
