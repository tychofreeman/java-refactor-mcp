package com.example;

public enum Color {
  RED("r"),
  GREEN("g"),
  BLUE("b");

  private final String code;

  Color(String code) {
    this.code = code;
  }
}
