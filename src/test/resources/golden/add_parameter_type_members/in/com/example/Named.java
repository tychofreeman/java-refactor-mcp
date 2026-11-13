package com.example;

class Named extends Box {
  Named() {
    super("n");
  }

  @Override
  public String get() {
    return "named " + super.get();
  }
}
