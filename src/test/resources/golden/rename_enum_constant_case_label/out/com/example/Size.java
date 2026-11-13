package com.example;

public enum Size {
  SMALL,
  BIG;

  Size next() {
    return this == SMALL ? BIG : this;
  }
}
