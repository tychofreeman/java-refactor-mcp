package com.example;

record Circle(double r) implements Shape {
  @Override
  public void show() {
    class Half {
      double of(double x) {
        return x / 2;
      }
    }
    System.out.println(new Half().of(r()));
  }
}
