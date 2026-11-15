package com.example;

class Names {
  static String label(int n) {
    return "#" + Integer.toString(n);
  }

  static void note(int n) {
    Ledger.total = n + 1;
  }
}
