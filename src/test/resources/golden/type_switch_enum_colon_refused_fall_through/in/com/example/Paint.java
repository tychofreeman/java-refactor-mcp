package com.example;

class Paint {
  String label(Color c) {
    switch (c) {
      case RED:
        System.out.println("red");
      case GREEN:
        return "g";
      default:
        return "?";
    }
  }
}
