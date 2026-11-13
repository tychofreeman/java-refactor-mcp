package com.example;

public enum Color {
  RED {
    @Override
    public int weight() {
      return ordinal() * 2;
    }
  },
  GREEN {
    @Override
    public int weight() {
      return ordinal();
    }
  },
  BLUE {
    @Override
    public int weight() {
      return ordinal();
    }
  };

  public abstract int weight();
}
