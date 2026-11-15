package com.example;

record Point(int left) implements HasX {
  @Override
  public int left() {
    return left;
  }
}
