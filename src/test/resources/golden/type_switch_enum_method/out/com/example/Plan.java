package com.example;

import java.time.LocalDate;

class Plan {
  LocalDate next(Schedule s) {
    return s.next();
  }
}
