package com.example;

class Paint {
  String label(Color c) {
    return switch (c) {
      case RED -> {
        String s = c.name();
        if (s.isEmpty()) {
          yield "none";
        }
        yield s.toLowerCase();
      }
      case GREEN, BLUE -> "cool";
    };
  }
}
