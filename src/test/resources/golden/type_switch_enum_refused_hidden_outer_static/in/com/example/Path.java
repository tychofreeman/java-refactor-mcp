package com.example;

public class Path {
  static final Path ARRAY = new Path();
  static final Path INNER = new Path();

  enum Kind {
    ARRAY,
    INNER
  }

  static Path of(Kind kind) {
    return switch (kind) {
      case ARRAY -> ARRAY;
      case INNER -> INNER;
    };
  }
}
