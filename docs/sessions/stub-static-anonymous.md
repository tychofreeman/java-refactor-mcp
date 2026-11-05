# A stub for a call in an anonymous class of a static method is static

- **Item:** the friction-log entry of 2026-11-05 ("a stub for a call in an anonymous
  class of a static method is an instance method"), hit in #170. The user's request
  of 2026-11-05 to fix the open bugs.
- **Date:** 2026-11-05
- **Branch:** stub-static-anonymous

## Decisions (this session)

- A bare call's stub goes in the nearest named type around the call; whether it is
  static is now decided by that type's member around the call, looked for through
  anonymous classes and lambdas. Before, the first member met (the anonymous class's
  own `run`, an instance method) decided, so the stub was an instance method and the
  edit was refused ("Cannot make a static reference").

## Notes

- Golden `stub_static_in_anonymous` red first with the friction log's error, then
  green; no other golden changed.
- `hand:` none.

## Measured

One `replace_declaration`; the golden by `create_file`.
