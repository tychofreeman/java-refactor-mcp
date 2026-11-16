package com.example;

public class Codes {
  Object shown(int score) {
    return grade(score);
  }

  private static int grade(int score) {
    if (score > 90) {
      return 'A';
    }
    return 'B';
  }
}
