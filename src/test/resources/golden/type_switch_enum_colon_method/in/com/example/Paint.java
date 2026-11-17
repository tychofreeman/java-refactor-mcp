package com.example;

class Paint {
  String label(Color c) {
    switch (c) {
      case RED:
        String s = "r";
        if (s.isEmpty()) {
          return "?";
        }
        return s + "!";
      case GREEN:
        throw new IllegalStateException("green");
      case BLUE:
        return Names.of(c);
    }
    throw new AssertionError(c);
  }
}
