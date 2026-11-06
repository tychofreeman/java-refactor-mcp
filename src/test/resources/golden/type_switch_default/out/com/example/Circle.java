package com.example;

record Circle(double r) implements Shape {
  @Override
  public double area() {
    return Math.PI * r() * r();
  }
}
