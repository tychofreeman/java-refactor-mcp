package com.example;

public class Order {
  private final String id;

  Order(String id) {
    this.id = id;
  }

  String describe(Shop.Line line) {
    return id + ": " + line.name;
  }
}
