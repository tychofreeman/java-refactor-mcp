package com.example;

import java.util.function.Function;

/**
 * A box.
 *
 * @param <E> what it holds
 */
public class Box<E> {
  private E value;

  E get() {
    return value;
  }

  <U> U map(Function<E, U> f) {
    return f.apply(value);
  }
}
