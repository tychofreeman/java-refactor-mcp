package p;
import java.util.List;
public class Main {
  private final StringBuilder log = new StringBuilder();
  public static void main(String[] args) {
    Main m = new Main();
    m.all(List.of("a", "b"));
    System.out.println(m.log);
  }
  private void note(String s) {
    log.append("[" + s + "]");
  }
  void all(List<String> files) {
    files.forEach(s -> log.append("[" + s + "]"));
  }
}
