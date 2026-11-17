package com.example;

public class Order {
  private OrderState state = OrderState.OPEN;
  private final java.util.List<Integer> seen = new java.util.ArrayList<>();

  void log() {
    System.out.println("order is " + state.code());
    seen.add(state.code());
  }

  boolean done() {
    return state.code() >= OrderState.CLOSED.code();
  }
}
