package com.example;

record Circle(double r) implements Shape {
  @Override
  public void show() {
    System.out.println(r());
  }
}
