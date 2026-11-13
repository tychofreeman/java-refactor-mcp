package com.example;

class Box<T> {
  private T value;

  @SuppressWarnings("unchecked")
  T cast(Object o) {
    return (T) o;
  }

  void put(T t) {
    value = t;
  }
}
