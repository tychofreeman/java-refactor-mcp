package p;

import java.util.Map;
import java.util.TreeMap;

class Store<V> {
  V last;

  String describe() {
    return "store " + last;
  }
}

class Repo<K extends Comparable<K>, V> extends Store<V> {
  final Map<K, V> cache = new TreeMap<>();

  void put(K key, V value) {
    cache.put(key, value);
    last = value;
  }

  K smallest() {
    K best = null;
    for (K key : cache.keySet()) {
      if (best == null || key.compareTo(best) < 0) {
        best = key;
      }
    }
    return best;
  }

  @Override
  String describe() {
    return "repo " + cache;
  }
}

public class Main {
  public static void main(String[] args) {
    Repo<String, Integer> repo = new Repo<>();
    repo.put("pear", 3);
    repo.put("apple", 5);
    System.out.println(repo.smallest());
    System.out.println(repo.describe());
    System.out.println(repo.last);
    Store<Integer> store = repo;
    System.out.println(store.describe());
  }
}
