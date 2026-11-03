package com.example;

class Grades {
  String grade(int score) {
    if (score < 0) {
      throw new IllegalArgumentException("negative");
    }
    if (score > 90) {
      return "A";
    }
    return "B";
  }
}
