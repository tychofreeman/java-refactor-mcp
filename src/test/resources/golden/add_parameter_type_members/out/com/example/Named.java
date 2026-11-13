package com.example;

class Named extends Box<String> {
  Named() {
    super("n");
  }

  @Override
  public String get() {
    return "named " + super.get();
  }
}
