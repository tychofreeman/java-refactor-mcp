package com.example;

class Alerts {
  void announce(Level level) {
    switch (level) {
      case HIGH -> Pager.page(level);
      default -> Log.note(level.name());
    }
  }
}
