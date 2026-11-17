package com.example;

class Paint {
  String label(Color c) {
    switch (c) {
      case RED:
        return "r";
      case GREEN:
        return "g";
    }
    return "?";
  }
}
