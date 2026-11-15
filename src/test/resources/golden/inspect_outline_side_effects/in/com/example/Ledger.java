package com.example;

import java.util.ArrayList;
import java.util.List;

class Ledger {
  static int total;
  private int count;
  private final List<String> lines = new ArrayList<>();

  Ledger(int count) {
    this.count = count;
  }

  int twice(int n) {
    return 2 * n;
  }

  void bump() {
    count++;
  }

  void log(String s) {
    System.out.println(s);
  }

  void logTwice(String s) {
    log(s);
    log(s);
  }

  List<String> upper(List<String> in) {
    List<String> out = new ArrayList<>();
    for (String s : in) {
      out.add(s.trim().toUpperCase());
    }
    return out;
  }

  void record(String s) {
    lines.add(s);
  }

  int fact(int n) {
    return n <= 1 ? 1 : n * fact(n - 1);
  }

  String label() {
    String name = Names.label(count);
    return name.isEmpty() ? "?" : name;
  }

  void note() {
    int before = total;
    Names.note(before);
  }

  int[] squares(int n) {
    int[] out = new int[n];
    for (int i = 0; i < n; i++) {
      out[i] = i * i;
    }
    return out;
  }

  void fill(int[] into) {
    for (int i = 0; i < into.length; i++) {
      into[i] = i;
    }
  }
}
