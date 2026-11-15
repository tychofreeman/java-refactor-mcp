package com.example;

class Grade {
  String of(int score, boolean late) {
    if (score > 90 && !late) {
      return "A";
    } else if (score > 80 || late) {
      for (int i = 0; i < 2; i++) {
        while (score > 85) {
          score--;
        }
      }
      return score > 82 ? "B" : "B-";
    }
    switch (score) {
      case 1, 2 -> score++;
      case 3 -> score--;
      default -> {}
    }
    try {
      return String.valueOf(score);
    } catch (RuntimeException e) {
      return "?";
    }
  }
}
