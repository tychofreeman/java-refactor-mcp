package com.example;

class Alerts {
  void announce(Level level) {
    switch (level) {
      case HIGH -> {
        Log.note("high");
        Pager.page(level);
      }
    }
  }
}
