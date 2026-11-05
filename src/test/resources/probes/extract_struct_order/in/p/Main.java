package p;
public class Main {
  static String log = "";
  static int w() { log += "w"; return 3; }
  static int h() { log += "h"; return 4; }
  static int area(int w, int h) { return w * h; }
  public static void main(String[] args) {
    int r = area(w(), h());
    System.out.println(r + " " + log);
  }
}
