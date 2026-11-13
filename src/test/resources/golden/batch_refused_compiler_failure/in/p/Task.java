package p;

interface Task<R, E extends Throwable> {
  R call() throws E;
}
