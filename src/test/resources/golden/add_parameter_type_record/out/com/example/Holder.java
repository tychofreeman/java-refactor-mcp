package com.example;

public record Holder<T>(T value, int count) {
  public Holder {
    if (count < 0) {
      throw new IllegalArgumentException("count");
    }
  }

  public T value() {
    return value;
  }

  Holder<T> twice() {
    return new Holder<>(value, count * 2);
  }
}
