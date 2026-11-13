package p;

import java.io.IOException;

class Report extends Titled {
  Report(String name) throws IOException {
    super(name, 1);
  }

  Report() {
    super("untitled");
  }
}
