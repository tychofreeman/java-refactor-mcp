package com.example;

class Named extends Box implements Comparable<Box> {
  Named() {
    super("n");
  }

  @Override
  public String get() {
    return "named " + super.get();
  }

  @Override
  public int compareTo(Box other) {
    return get().compareTo(other.get());
  }
}
