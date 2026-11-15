package com.example;

public interface Source {
  Source EMPTY = () -> "";

  String next();

  default Source then(Source other) {
    return other;
  }
}
