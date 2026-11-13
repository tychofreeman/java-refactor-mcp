package com.example;

public sealed interface Shape {
  record Circle(double r) implements Shape {
    @Override
    public double area() {
      return Math.PI * r() * r();
    }
  }

  record Square(double side) implements Shape {
    @Override
    public double area() {
      return side() * side();
    }
  }

  double area();
}
