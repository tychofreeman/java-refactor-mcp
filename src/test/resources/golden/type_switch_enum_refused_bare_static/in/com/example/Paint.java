package com.example;

class Paint {
  static final int HEAVY = 3;

  int weight(Color c) {
    int w = switch (c) {
      case GREEN, BLUE -> 2;
      case RED -> HEAVY;
    };
    return w;
  }
}
