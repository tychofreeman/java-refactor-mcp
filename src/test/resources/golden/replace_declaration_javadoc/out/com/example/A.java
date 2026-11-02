package com.example;

public class A {
  int a() {
    return b();
  }

  /** B, now two. */
  int b() {
    return 2;
  }
}
