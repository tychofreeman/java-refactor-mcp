package com.example;

public enum Level {
  LOW,
  HIGH {
    @Override
    public void announce() {
      Log.note("high");
      Pager.page(this);
    }
  };

  public void announce() {}
}
