package com.example;

public enum Op {
  ADD {
    @Override
    public int arity() {
      return 2;
    }
  },
  SUB {
    @Override
    public int arity() {
      return 2;
    }
  },
  NOP;

  public int arity() {
    throw new IllegalArgumentException("no arity: " + this);
  }
}
