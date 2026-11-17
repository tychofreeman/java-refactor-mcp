package com.example;

public class Order {
  private OrderState state = OrderState.OPEN;

  void reset() {
    state = OrderState.OPEN;
  }

  String label() {
    switch (state) {
      case SHIPPED:
        return "shipped";
      case CLOSED:
        return "closed";
      default:
        return "open";
    }
  }
}
