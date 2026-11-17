package com.example;

record Line(OrderState state) {
  boolean open() {
    return state == OrderState.OPEN;
  }
}
