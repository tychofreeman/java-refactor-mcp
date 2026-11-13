package p;
import java.util.List;
public class Main {
  public static void main(String[] args) {
    List<String> xs = List.of("a", "b");
    Lists.print(Lists.first(xs));
    Object o = Lists.first(xs);
    Lists.print(o);
    System.out.println(Lists.first(xs).toUpperCase());
  }
}
