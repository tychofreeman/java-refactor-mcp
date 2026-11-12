package com.example;

class Walk {
  static int depth(Node n) {
    return n == null ? 0 : 1 + depth(n.next);
  }

  int size(Node n) {
    return depth(n);
  }
}
