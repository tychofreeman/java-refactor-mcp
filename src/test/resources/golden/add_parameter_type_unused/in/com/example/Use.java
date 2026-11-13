package com.example;

public class Use {
  private final Registry registry = new Registry();

  int count(Registry other) {
    registry.register("a");
    return registry.size() + other.size();
  }
}
