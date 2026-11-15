package com.example;

public interface Source<T> {
  Source<String> EMPTY = () -> "";

  T next();

  default Source<T> then(Source<T> other) {
    return other;
  }
}
