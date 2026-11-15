package com.example;

public record Holder(String value, int count) {
  public Holder {
    if (count < 0) {
      throw new IllegalArgumentException("count");
    }
  }

  public String value() {
    return value;
  }

  Holder twice() {
    return new Holder(value, count * 2);
  }
}
