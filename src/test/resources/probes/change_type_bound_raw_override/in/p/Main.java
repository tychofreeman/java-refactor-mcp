package p;
public class Main {
  @SuppressWarnings({"rawtypes", "unchecked"})
  static class Legacy extends Box {
    void put(Object o) { System.out.println("legacy " + o); }
  }
  @SuppressWarnings("unchecked")
  public static void main(String[] args) {
    Box<String> b = new Legacy();
    b.put("a");
  }
}
