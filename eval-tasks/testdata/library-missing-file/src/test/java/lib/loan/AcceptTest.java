package lib.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcceptTest {
  @Test
  void aMissingCatalogIsAnEmptyLibrary(@TempDir Path dir) throws Exception {
    assertEquals(0, Library.load(dir.resolve("none.csv")).copies("111"));
  }

  @Test
  void anotherReadErrorStillThrows(@TempDir Path dir) {
    assertThrows(IOException.class, () -> Library.load(dir));
  }
}
