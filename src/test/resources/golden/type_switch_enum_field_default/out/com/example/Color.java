package com.example;

public enum Color {
  RED(null),
  GREEN("cool"),
  BLUE("cool");

  private final String label;

  Color(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
