package com.example;

class Walk {
  static int depth(Node receiver) {
    return receiver == null ? 0 : 1;
  }

  int size(Node n) {
    return depth(n);
  }
}
