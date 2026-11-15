package com.example;

record Square(double side) implements Shape {
  @Override
  public void show() {
    double side = side();
    System.out.println(side);
  }
}
