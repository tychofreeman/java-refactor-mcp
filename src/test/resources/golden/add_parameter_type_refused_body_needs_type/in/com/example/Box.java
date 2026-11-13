package com.example;

class Box {
  private final String value;

  Box(String value) {
    this.value = value;
  }

  int size() {
    return value.length();
  }
}
