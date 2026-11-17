package com.example.ui;

import com.example.OrderState;

class Printer implements com.example.Handler {
  @Override
  public void handle(OrderState state) {
    if (state != OrderState.CLOSED && state != OrderState.SHIPPED) {
      System.out.println("still open");
    }
  }
}
