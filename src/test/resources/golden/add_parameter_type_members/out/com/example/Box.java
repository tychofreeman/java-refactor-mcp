package com.example;

import java.util.List;

public class Box<T> {
  private final T value;

  public Box(T value) {
    this.value = value;
  }

  public T get() {
    return value;
  }

  public Box<T> copy() {
    return new Box<>(value);
  }

  public boolean same(Box<T> other, List<T> seen) {
    return other.value.equals(value) && !seen.isEmpty();
  }

  public int size() {
    return 1;
  }
}
