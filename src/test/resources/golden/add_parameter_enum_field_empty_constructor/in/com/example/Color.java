package com.example;

public enum Color {
  RED,
  GREEN();

  Color() {}

  static Color first() {
    return RED;
  }
}
