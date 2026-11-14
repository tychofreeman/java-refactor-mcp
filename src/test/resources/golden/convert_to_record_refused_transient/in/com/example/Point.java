package com.example;

public final class Point implements java.io.Serializable {
  private final int x;
  private final transient int y;

  public Point(int x, int y) {
    this.x = x;
    this.y = y;
  }
}
