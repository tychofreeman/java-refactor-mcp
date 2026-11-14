package com.example;

sealed class Shape implements Named permits Circle {
  public String name() {
    return "shape";
  }
}

final class Circle extends Shape {}
