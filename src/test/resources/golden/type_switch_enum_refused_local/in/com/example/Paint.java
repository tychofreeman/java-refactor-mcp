package com.example;

class Paint {
  String label(Color c, String suffix) {
    return switch (c) {
      case RED -> "r" + suffix;
      case GREEN, BLUE -> "cool";
    };
  }
}
