package com.example;

public class Order {
  void tell(Handler h) {
    h.handle(OrderState.OPEN);
  }
}
