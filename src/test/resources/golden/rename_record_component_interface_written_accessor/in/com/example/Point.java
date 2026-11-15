package com.example;

record Point(int x) implements HasX {
  @Override
  public int x() {
    return x;
  }
}
