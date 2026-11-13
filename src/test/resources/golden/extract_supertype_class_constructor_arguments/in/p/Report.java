package p;

import java.io.IOException;

class Report extends Doc {
  Report(String name) throws IOException {
    super(name, 1);
  }

  Report() {
    super("untitled");
  }

  String title() {
    return name.toUpperCase();
  }
}
