package com.example;

public class Order {
  private OrderState state = OrderState.OPEN;

  public OrderState state() {
    return state;
  }

  public void ship() {
    if (state == OrderState.OPEN) {
      state = OrderState.SHIPPED;
    }
  }
}
