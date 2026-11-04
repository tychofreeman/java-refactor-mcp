# Name Stubs' default-constructor flag for what it means

- **Item:** order 49 (row 42): `Stubs` names `implicit` the flag that is true
  when a default-constructor stub should be declared, which is the inverse of
  what the name says. The `Extract` part of the row is done (`declaredInside`).
  Taken by the cloud session from the next-ten list (2026-11-04).
- **Date:** 2026-11-04
- **Branch:** stubs-implicit-name

## Plan

The change, at the highest level: the flag's name says what its value means.

Operations, in order:

1. Rename the local `implicit` in `Stubs` to `needsDefault` with `rename`
   (line of its declaration); its uses follow. No behaviour change.
2. The check: the existing goldens and `./mvnw verify`. No new golden: a rename
   changes no output.

## Notes

