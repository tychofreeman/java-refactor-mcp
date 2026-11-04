package com.example;

public interface Visitor {
  Order order();

  /** The order to visit in. */
  enum Order {
    FIRST
  }
}
