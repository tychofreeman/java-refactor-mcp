package com.example;

class Paint {
  String label(Color c) {
    return switch (c) {
      case RED -> "r";
      case GREEN, BLUE -> "cool";
    };
  }
}
