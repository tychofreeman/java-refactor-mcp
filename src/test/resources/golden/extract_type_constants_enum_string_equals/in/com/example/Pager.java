package com.example;

class Pager {
  boolean urgent(String level) {
    return Levels.HIGH.equals(level);
  }

  void run() {
    if (urgent(Levels.LOW)) {
      page();
    }
  }

  void page() {}
}
