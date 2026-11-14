package com.example;

public class Holder {
  private final Counter counter = new Counter();

  void tick() {
    counter.reset();
  }
}
