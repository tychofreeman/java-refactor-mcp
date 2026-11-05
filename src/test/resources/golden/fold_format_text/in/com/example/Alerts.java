package com.example;

import java.util.List;
import java.util.Locale;

public class Alerts {
  static void alert(List<String> out, String room, String what, double v) {
    out.add(String.format(Locale.ROOT, "%s: %s %.1f", room, what, v));
  }

  void temp(List<String> out, String room, double v) {
    out.add(String.format(Locale.ROOT, "%s: temp %.1f", room, v));
  }

  void dust(List<String> out, String room, double v) {
    out.add(String.format(Locale.ROOT, "%s: \"dust\" %.1f", room, v));
  }

  void other(List<String> out, String room, double v) {
    out.add(String.format(Locale.ROOT, "%s %.1f", room, v));
  }
}
