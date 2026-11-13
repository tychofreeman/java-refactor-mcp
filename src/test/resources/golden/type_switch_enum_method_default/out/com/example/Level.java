package com.example;

public enum Level {
  LOW,
  HIGH {
    @Override
    public void announce() {
      Pager.page(this);
    }
  };

  public void announce() {
    Log.note(name());
  }
}
