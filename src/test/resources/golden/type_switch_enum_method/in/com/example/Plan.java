package com.example;

import java.time.LocalDate;

class Plan {
  LocalDate next(Schedule s) {
    return switch (s) {
      case DAILY -> LocalDate.now().plusDays(1);
      case WEEKLY -> LocalDate.now().plusWeeks(1);
    };
  }
}
