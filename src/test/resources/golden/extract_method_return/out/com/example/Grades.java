package com.example;

class Grades {
  String grade(int score) {
    if (score < 0) {
      throw new IllegalArgumentException("negative");
    }
    return letter(score);
  }

  private String letter(int score) {
    if (score > 90) {
      return "A";
    }
    return "B";
  }
}
