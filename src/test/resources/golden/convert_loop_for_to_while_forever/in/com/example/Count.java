package com.example;

public class Count {
  int first(int[] a) {
    for (int i = 0; ; i++) {
      if (a[i] > 0) {
        return i;
      }
    }
  }
}
