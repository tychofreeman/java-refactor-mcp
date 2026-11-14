package com.example;

record Square(double side) implements Shape {
  @Override
  public void show() {
    System.out.println(side());
  }
}
