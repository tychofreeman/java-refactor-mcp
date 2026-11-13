package com.example;

public enum Size {
  SMALL("s"),
  LARGE("l") {
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
    this("?");
  }

  Size(String code) {
    this.code = code;
  }

  public boolean bulky() {
    return false;
  }
}
