package com.example;

public class Count {
  void count() {
    int i = 0;
    outer:
    do i++; while (i < 3);
    System.out.println(i);
  }
}
