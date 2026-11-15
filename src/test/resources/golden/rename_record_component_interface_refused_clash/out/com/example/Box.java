package com.example;

class Box implements HasX {
  @Override
  public int x() {
    return 3;
  }

  public int left() {
    return 4;
  }
}
