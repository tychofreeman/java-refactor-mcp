package p;
public class Main {
  public static void main(String[] args) {
    int[] values = {1, 2, 3};
    for (int i = 0; i < values.length; i++) {
      (values[i]) = 0;
    }
    System.out.println(values[0] + values[1] + values[2]);
  }
}
