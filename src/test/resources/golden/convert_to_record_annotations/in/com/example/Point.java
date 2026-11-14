package com.example;

public final class Point {
  @Deprecated private final int x;

  @SuppressWarnings("unused")
  private final int y;

  public Point(int x, int y) {
    this.x = x;
    this.y = y;
  }
}
