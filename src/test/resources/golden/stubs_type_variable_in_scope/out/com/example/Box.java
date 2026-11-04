package com.example;

class Box<T> {
  void keep(T item) {
    store(item);
  }

  void store(T item) {
    throw new UnsupportedOperationException("not implemented");
  }
}
