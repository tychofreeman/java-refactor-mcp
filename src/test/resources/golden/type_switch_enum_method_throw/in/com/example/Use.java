package com.example;

class Use {
  static int arity(Op op) {
    return switch (op) {
      case ADD, SUB -> 2;
      default -> throw new IllegalArgumentException("no arity: " + op);
    };
  }
}
