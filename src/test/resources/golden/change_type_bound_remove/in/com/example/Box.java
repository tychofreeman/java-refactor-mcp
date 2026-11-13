package com.example;

class Box<T extends Comparable<T>> {
  private T value;

  T get() {
    return value;
  }
}
