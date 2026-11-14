package com.example;

abstract class Square implements Shapes.Shape {
  @Override
  public double area() {
    throw new UnsupportedOperationException("not implemented");
  }
}
