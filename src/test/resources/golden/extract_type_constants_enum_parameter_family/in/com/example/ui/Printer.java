package com.example.ui;

import static com.example.Order.CLOSED;

import com.example.Order;

class Printer implements com.example.Handler {
  @Override
  public void handle(int state) {
    if (state != CLOSED && state != Order.SHIPPED) {
      System.out.println("still open");
    }
  }
}
