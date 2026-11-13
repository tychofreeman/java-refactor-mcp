package com.example;

class Paint {
  String label(Color c) {
    return switch (c) {
      case RED -> c.name().toLowerCase();
      case GREEN -> "g".repeat(2);
    };
  }
}
