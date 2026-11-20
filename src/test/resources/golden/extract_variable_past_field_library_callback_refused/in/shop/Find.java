package shop;

import java.util.List;

class Find {
  static class Counter {
    int n;
  }

  static class Item {
    Counter seen;

    @Override
    public boolean equals(Object o) {
      seen.n++;
      return o == this;
    }

    @Override
    public int hashCode() {
      return 0;
    }
  }

  static void add(List<Item> items, Item item, Counter counter) {
    counter.n += items.indexOf(item);
  }
}
