package com.example;

public class Order {
  /** Not shipped yet. */
  public static final int OPEN = 0;
  public static final int SHIPPED = 1;
  public static final int CLOSED = 2;

  void tell(Handler h) {
    h.handle(OPEN);
  }
}
