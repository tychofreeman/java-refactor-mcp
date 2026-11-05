package com.example;

/** A point. */
public final class Point {
  private final int x;
  private final int y;

  public Point(int x, int y) {
    this.x = x;
    this.y = y;
  }

  int x() {
    return x;
  }

  public int getY() {
    return y;
  }
}
