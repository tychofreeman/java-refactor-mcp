package com.example;

class Paint {
  String label(Color c) {
    return switch (c) {
      case RED -> null;
      default -> "cool";
    };
  }
}
