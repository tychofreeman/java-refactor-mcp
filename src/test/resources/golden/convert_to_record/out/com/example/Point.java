package com.example;

/** A point. */
public record Point(int x, int y) {
  public int x() {
    return x;
  }

  public int getY() {
    return y;
  }
}
