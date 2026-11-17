package com.example;

public class Order {
  public static final int OPEN = 1;
  public static final int CLOSED = 2;

  private int state;

  boolean open() {
    return state == OPEN;
  }
}
