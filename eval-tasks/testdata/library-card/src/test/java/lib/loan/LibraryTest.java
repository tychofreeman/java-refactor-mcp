package lib.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryTest {
  @Test
  void loadsTheCatalog(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("catalog.csv");
    Files.writeString(file, "111, 2\n222,1\n");
    Library library = Library.load(file);
    assertEquals(2, library.copies("111"));
    assertEquals(0, library.copies("333"));
  }

  @Test
  void aDayLateCosts25() {
    assertEquals(25, Fines.fine(1));
  }
}
