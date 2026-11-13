package com.example;

public enum Color {
  RED,
  GREEN; // the last one

  String lower() {
    return name().toLowerCase();
  }
}
