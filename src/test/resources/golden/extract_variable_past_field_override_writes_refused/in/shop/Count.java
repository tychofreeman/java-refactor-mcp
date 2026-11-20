package shop;

class Count {
  static class Counter {
    int n;
  }

  static class Source {
    Counter seen = new Counter();

    int next() {
      return 1;
    }
  }

  static class Bumping extends Source {
    @Override
    int next() {
      seen.n++;
      return 1;
    }
  }

  static void add(Source source, Counter counter) {
    counter.n += source.next();
  }
}
