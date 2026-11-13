package p;
import java.util.Collection;
import java.util.List;
public class Main {
  static void addAll(List<Object> items) { System.out.println("list " + items.size()); }
  static void addAll(Collection<?> items) { System.out.println("collection " + items.size()); }
  public static void main(String[] args) {
    List<Object> objects = List.of(1, 2);
    List<String> names = List.of("a");
    addAll(objects);
    addAll(names);
  }
}
