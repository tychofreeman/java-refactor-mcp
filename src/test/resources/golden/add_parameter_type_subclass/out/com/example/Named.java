package com.example;

class Named extends Box<String> implements Comparable<Box<String>> {
  Named() {
    super("n");
  }

  @Override
  public String get() {
    return "named " + super.get();
  }

  @Override
  public int compareTo(Box<String> other) {
    return get().compareTo(other.get());
  }
}
