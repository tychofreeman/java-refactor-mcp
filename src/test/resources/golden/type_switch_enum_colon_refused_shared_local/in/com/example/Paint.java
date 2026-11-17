package com.example;

class Paint {
  String label(Color c) {
    switch (c) {
      case RED:
        String s = "r";
        return s;
      default:
        s = "?";
        return s;
    }
  }
}
