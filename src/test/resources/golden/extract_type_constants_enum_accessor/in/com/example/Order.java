package com.example;

public class Order {
  public static final int OPEN = 0;
  public static final int SHIPPED = 1;
  public static final int CLOSED = 2;

  private int state = OPEN;
  private final java.util.List<Integer> seen = new java.util.ArrayList<>();

  void log() {
    System.out.println("order is " + state);
    seen.add(state);
  }

  boolean done() {
    return state >= CLOSED;
  }
}
