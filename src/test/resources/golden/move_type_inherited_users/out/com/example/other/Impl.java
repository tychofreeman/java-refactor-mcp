package com.example.other;

import com.example.Order;
import com.example.Visitor;

public class Impl implements Visitor {
  @Override
  public Order order() {
    return Order.FIRST;
  }
}
