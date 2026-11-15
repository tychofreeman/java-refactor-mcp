package com.example;

import java.util.List;

public class Bed {
  /** Plants {@link Plant#species}. */
  int score(Plant p, Object o, List<CharSequence> names) {
    if (p.species == "cactus") {
      return 0;
    }
    switch (p.species) {
      case "fern":
        return 1;
      default:
        break;
    }
    names.add(p.species);
    p.species = "rose";
    if (o instanceof Plant q) {
      return q.water(2);
    }
    Plant made = new Plant("fern");
    int h = ((Plant) o).water(1) + made.height;
    java.util.function.IntUnaryOperator w = made::water;
    return switch (o) {
      case Plant r -> r.water(3);
      default -> h;
    };
  }
}
