package com.example;

public class Count {
  int first(int[] a) {
    int i = 0;
    while (true) {
      if (a[i] > 0) {
        return i;
      }
      i++;
    }
  }
}
