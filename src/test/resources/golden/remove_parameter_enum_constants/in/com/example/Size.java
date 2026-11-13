package com.example;

public enum Size {
  SMALL("s", 1),
  LARGE("l", 3) {
    @Override
    public boolean bulky() {
      return true;
    }
  };

  final String code;

  Size(String code, int weight) {
    this.code = code;
  }

  public boolean bulky() {
    return false;
  }
}
