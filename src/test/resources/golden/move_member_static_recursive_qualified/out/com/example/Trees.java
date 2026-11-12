package com.example;

class Trees {
  static int depth(Node n) {
    return n == null ? 0 : 1 + depth(n.next);
  }
}
