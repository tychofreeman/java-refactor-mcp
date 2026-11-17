package com.example;

public class Order {
  public static final int OPEN = 0;
  public static final int SHIPPED = 1;
  public static final int CLOSED = 2;

  Line first() {
    return new Line(OPEN);
  }
}
