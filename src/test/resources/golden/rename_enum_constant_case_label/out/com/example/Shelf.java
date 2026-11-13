package com.example;

class Shelf {
  Size widest = Size.BIG;

  String label(Size s) {
    switch (s) {
      case BIG:
        return "large";
      default:
        return "other";
    }
  }

  int rank(Size s) {
    return switch (s) {
      case SMALL -> 0;
      case BIG -> 1;
    };
  }
}
