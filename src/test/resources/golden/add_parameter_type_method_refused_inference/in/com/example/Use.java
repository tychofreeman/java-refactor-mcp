package com.example;

import java.util.ArrayList;

class Use {
  boolean run() {
    Object o = Lists.first(new ArrayList<>());
    return o == null;
  }
}
