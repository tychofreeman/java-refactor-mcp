package com.example;

public enum Size {
  SMALL("s", 1),
  LARGE("l", 3) {
    @Override
    public boolean bulky() {
      return true;
    }
  };

  private final String code;

  private final int weight;

  Size(String code, int weight) {
    this.code = code;
    this.weight = weight;
  }

  public boolean bulky() {
    return false;
  }

  public int weight() {
    return weight;
  }
}
