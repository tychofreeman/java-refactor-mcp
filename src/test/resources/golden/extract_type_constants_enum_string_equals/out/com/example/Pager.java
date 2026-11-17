package com.example;

class Pager {
  boolean urgent(Level level) {
    return Level.HIGH.equals(level);
  }

  void run() {
    if (urgent(Level.LOW)) {
      page();
    }
  }

  void page() {}
}
