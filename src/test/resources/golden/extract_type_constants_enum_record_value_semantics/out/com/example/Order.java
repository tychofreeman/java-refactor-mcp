package com.example;

public class Order {
  Line first() {
    return new Line(OrderState.OPEN);
  }
}
