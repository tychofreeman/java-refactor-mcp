package com.example;

public enum Color {
  RED(true, "r"),
  GREEN(false, "g");

  private final String code;

  private final boolean warm;

  Color(boolean warm, String code) {
    this.code = code;
    this.warm = warm;
  }

  public String code() {
    return code;
  }

  public boolean warm() {
    return warm;
  }
}
