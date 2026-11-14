package com.example;

public class Main {
  public static void main(String[] args) {
    System.out.println(count(new int[] {1, -2, 3}));
  }

  /**
   * The positive numbers among {@code xs}: a comment long enough to wrap onto a second line, as the
   * formatter writes it.
   */
  static int count(int[] xs) {
    int n = 0;
    for (int x : xs) {
      if (x > 0) {
        n++;
      }
    }
    return n;
  }
}
