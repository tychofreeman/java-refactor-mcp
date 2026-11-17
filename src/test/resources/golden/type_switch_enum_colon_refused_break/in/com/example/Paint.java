package com.example;

class Paint {
  String label(Color c) {
    String s = "?";
    switch (c) {
      case RED:
        if (c.ordinal() > 5) {
          break;
        }
        return "r";
      default:
        return "?";
    }
    return s;
  }
}
