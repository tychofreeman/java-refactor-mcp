package com.example;

public enum OrderState {
  OPEN(0),
  SHIPPED(1),
  CLOSED(2);

  private final int code;

  OrderState(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
