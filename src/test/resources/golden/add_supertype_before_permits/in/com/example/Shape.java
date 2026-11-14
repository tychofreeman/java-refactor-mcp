package com.example;

sealed class Shape permits Circle {
  public String name() {
    return "shape";
  }
}

final class Circle extends Shape {}
