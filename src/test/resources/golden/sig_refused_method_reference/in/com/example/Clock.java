package com.example;

class Clock {
  void tick() {
    System.out.println("tick");
  }

  Runnable ticker() {
    return this::tick;
  }
}
