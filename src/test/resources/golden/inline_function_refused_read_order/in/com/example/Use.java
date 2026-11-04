package com.example;

public class Use {
  int all() {
    return Lib.f(Counter.bump(), Counter.count);
  }
}
