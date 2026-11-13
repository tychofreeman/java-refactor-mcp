package com.example;

public enum Color {
  RED(""),
  GREEN("");

  private final String code;

  Color(String code) {
    this.code = code;
  }

  static Color first() {
    return RED;
  }

  public String code() {
    return code;
  }
}
