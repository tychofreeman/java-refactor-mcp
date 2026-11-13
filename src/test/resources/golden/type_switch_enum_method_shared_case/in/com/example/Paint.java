package com.example;

class Paint {
  int weight(Color c) {
    return switch (c) {
      case RED -> c.ordinal() * 2;
      case GREEN, BLUE -> c.ordinal();
    };
  }
}
