package com.example;

class Clock {
  static final int STEP = 5;

  long nanos() {
    long seconds = STEP;
    return seconds * 1_000_000_000;
  }

  long tick() {
    return nanos() + nanos();
  }
}
