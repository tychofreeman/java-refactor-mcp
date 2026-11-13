package com.example;

public enum Color {
  RED {
    @Override
    public String label() {
      return "r";
    }
  },
  GREEN {
    @Override
    public String label() {
      return "cool";
    }
  },
  BLUE {
    @Override
    public String label() {
      return "cool";
    }
  };

  public abstract String label();
}
