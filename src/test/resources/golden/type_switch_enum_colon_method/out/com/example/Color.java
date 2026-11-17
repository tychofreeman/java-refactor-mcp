package com.example;

public enum Color {
  RED {
    @Override
    public String label() {
      String s = "r";
      if (s.isEmpty()) {
        return "?";
      }
      return s + "!";
    }
  },
  GREEN {
    @Override
    public String label() {
      throw new IllegalStateException("green");
    }
  },
  BLUE {
    @Override
    public String label() {
      return Names.of(this);
    }
  };

  public abstract String label();
}
