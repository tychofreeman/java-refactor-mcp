package com.example;

public class Order {
  public static final int OPEN = 0;
  public static final int SHIPPED = 1;
  public static final int CLOSED = 2;

  private final java.util.List<Integer> seen = new java.util.ArrayList<>();

  void ship() {
    seen.add(SHIPPED);
  }
}
