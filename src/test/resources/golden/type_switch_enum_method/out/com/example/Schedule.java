package com.example;

import java.time.LocalDate;

public enum Schedule {
  DAILY {
    @Override
    public LocalDate next() {
      return LocalDate.now().plusDays(1);
    }
  },
  WEEKLY {
    @Override
    public LocalDate next() {
      return LocalDate.now().plusWeeks(1);
    }
  };

  public abstract LocalDate next();
}
