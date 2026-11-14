package com.example;

class Circle implements Shape {
  @Override
  public String toString() {
    return "circle";
  }

  @Override
  public void show() {
    Circle circle = this;
    Runnable r =
        new Runnable() {
          public void run() {
            System.out.println(circle);
          }
        };
    r.run();
  }
}
