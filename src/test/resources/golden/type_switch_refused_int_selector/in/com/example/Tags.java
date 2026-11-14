package com.example;

class Tags {
  static final int TAG_INT = 3;
  static final int TAG_LONG = 5;

  static int size(int tag) {
    return switch (tag) {
      case TAG_INT -> 1;
      case TAG_LONG -> {
        int words = 2;
        yield words;
      }
      default -> 0;
    };
  }
}
