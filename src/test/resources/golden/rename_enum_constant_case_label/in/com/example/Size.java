package com.example;

public enum Size {
  SMALL,
  LARGE;

  Size next() {
    return this == SMALL ? LARGE : this;
  }
}
