package com.example;

import java.util.List;
import java.util.stream.Stream;

class Main {
  static int total(List<Integer> xs) {
    int sum = xs.stream().mapToInt(Integer::intValue).sum();
    return sum;
  }

  static int count(List<Integer> xs) {
    int n = xs.size();
    return n;
  }

  static Stream<Integer> evens(List<Integer> xs) {
    return xs.stream().filter(x -> x % 2 == 0);
  }
}
