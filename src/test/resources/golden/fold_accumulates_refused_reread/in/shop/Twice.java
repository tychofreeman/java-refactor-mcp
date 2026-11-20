package shop;

class Twice {
  static String grow(String s) {
    s += s.length();
    s += s.length();
    return s;
  }

  static String step(String acc, int n) {
    acc += n;
    acc += n;
    return acc;
  }
}
