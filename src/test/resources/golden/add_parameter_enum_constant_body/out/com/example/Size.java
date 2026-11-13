package com.example;

public enum Size {
  SMALL("s", 1),
  LARGE("l", 1) {
    @Override
    public boolean bulky() {
      return true;
    }
  },
  ODD {
    @Override
    public boolean bulky() {
      return code == null;
    }
  };

  final String code;

  Size() {
    this("?", 1);
  }

  Size(String code, int weight) {
    this.code = code;
  }

  public boolean bulky() {
    return false;
  }
}
