package com.example;

public class Order {
  public static final int OPEN = 0;
  public static final int SHIPPED = 1;
  public static final int CLOSED = 2;

  private int state = OPEN;

  void reset() {
    state = 0;
  }

  String label() {
    switch (state) {
      case 1:
        return "shipped";
      case CLOSED:
        return "closed";
      default:
        return "open";
    }
  }
}
