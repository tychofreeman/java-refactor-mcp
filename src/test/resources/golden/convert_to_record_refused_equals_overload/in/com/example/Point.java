package com.example;

public final class Point {
  private final int x;
  private final int y;

  public Point(int x, int y) {
    this.x = x;
    this.y = y;
  }

  public boolean equals(Point p) {
    return p == this;
  }

  @Override
  public int hashCode() {
    return x;
  }

  @Override
  public String toString() {
    return "Point";
  }
}
