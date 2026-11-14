package p;
public class Main {
  static int average(java.util.List<Integer> items, int total, int count) {
    if (items.isEmpty()) {
      return 0;
    }
    int avg = total / count;
    return avg;
  }
  public static void main(String[] args) {
    System.out.println(average(java.util.List.of(1, 2), 10, 2) + " " + average(java.util.List.of(), 9, 3));
  }
}
