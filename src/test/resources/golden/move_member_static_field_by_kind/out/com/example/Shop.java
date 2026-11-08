package com.example;

class Shop {
  int cap(int n) {
    return Math.min(n, Config.MAX);
  }

  static int twice() {
    return Config.MAX * 2;
  }
}
