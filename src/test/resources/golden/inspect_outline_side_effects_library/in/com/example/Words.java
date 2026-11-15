package com.example;

import java.util.List;

class Words {
  private int seen;

  String join(List<String> words) {
    StringBuilder out = new StringBuilder();
    for (String w : words) {
      out.append(w).append(' ');
    }
    return out.toString().strip();
  }

  long blanks(List<String> words) {
    return words.stream().filter(String::isBlank).count();
  }

  void see(List<String> words) {
    words.forEach(w -> seen++);
  }

  double area(Shape shape) {
    return Math.abs(shape.area());
  }

  Runnable greeter() {
    return () -> System.out.println("hello");
  }

  int[] copy(int[] from) {
    int[] to = new int[from.length];
    System.arraycopy(from, 0, to, 0, from.length);
    return to;
  }

  void sort(int[] numbers) {
    java.util.Arrays.sort(numbers);
  }

  interface Shape {
    double area();
  }
}
