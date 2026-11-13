package com.example;

public enum Size {
  SMALL("s"),
  LARGE("l") {
    @Override
    public boolean bulky() {
      return true;
    }
  };

  private final String code;

  Size(String code) {
    this.code = code;
  }

  public boolean bulky() {
    return false;
  }
}
