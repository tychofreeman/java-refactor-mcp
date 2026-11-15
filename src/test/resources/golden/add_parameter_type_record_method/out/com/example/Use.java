package com.example;

import java.util.List;

class Use {
  int run(List<String> names) {
    return new Names(names).pick(names).length();
  }
}
