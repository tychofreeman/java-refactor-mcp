package com.example;

public class Use {
  private final Registry<String> registry = new Registry<>();

  int count(Registry<String> other) {
    registry.register("a");
    return registry.size() + other.size();
  }
}
