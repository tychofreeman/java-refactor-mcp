package com.example;

class Shelf {
  Size widest = Size.LARGE;

  String label(Size s) {
    switch (s) {
      case LARGE:
        return "large";
      default:
        return "other";
    }
  }

  int rank(Size s) {
    return switch (s) {
      case SMALL -> 0;
      case LARGE -> 1;
    };
  }
}
