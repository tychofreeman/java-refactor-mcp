package com.example;

final class Circle implements Shape {
  double radius = 3;

  @Override
  public void show() {
    double radius = 2;
    System.out.println(this.radius * radius);
  }
}
