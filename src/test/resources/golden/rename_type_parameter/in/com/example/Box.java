package com.example;

import java.util.function.Function;

/**
 * A box.
 *
 * @param <T> what it holds
 */
public class Box<T> {
  private T value;

  T get() {
    return value;
  }

  <U> U map(Function<T, U> f) {
    return f.apply(value);
  }
}
