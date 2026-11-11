package p;
import java.util.Iterator;
import java.util.List;
public class Main {
  public static void main(String[] args) {
    List<String> names = List.of("a", "b", "c");
    String out = "";
    for (Iterator<String> it = names.iterator(); it.hasNext(); ) {
      String name = it.next();
      out += name.toUpperCase() + name;
    }
    System.out.println(out);
  }
}