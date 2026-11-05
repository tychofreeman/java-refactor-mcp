package com.example;

import java.util.List;

public class Log {
  static void record(List<String> out, String who, int n) {
    String line = who + n;
    out.add(who);
    out.add(line);
  }
}

class Use {
  void f(List<String> lines, int k) {
    String text = "a" + k;
    lines.add("a");
    lines.add(text);
    k++;
  }
}
