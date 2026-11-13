package p;
import java.util.List;
public class Lists {
  static String first(List<String> xs) { return xs.get(0); }
  static void print(String s) { System.out.println("string " + s); }
  static void print(Object o) { System.out.println("object " + o); }
}
