# inline_function (the third of the ten higher-level tools)

**Item:** replace every call of a method with its body, then delete the method.
**Source:** the composite comparison of 2026-11-04 (Inline Function), option B
of the 2026-11-04 choice, after `extract_variable` and `inline_variable`
(the user's "continue with inline_function", 2026-11-04).
**Date:** 2026-11-04

## Plan

Change at the highest level: a static method is named as `Type.method`, and
the tool rewrites every call in the project to its body with the arguments put
in place of the parameters, then removes the method, in one edit under the gate.

Operations, in order:
1. Find the method by `Type.name` in a top-level class of the file (reusing
   `MemberMove.member`, which also refuses overloads and names the choices).
2. Refuse what v1 does not inline: an instance method (it needs its receiver),
   a varargs or generic method, an abstract or native one, a method that calls
   itself, a body that is not one `return` or one statement, and a method with
   no calls in the project.
3. Find every call in the project with the method's binding. Refuse a method
   used as a method reference (`Type::name`), and calls nested in another
   call's arguments (they would overlap).
4. Substitute: each parameter in the body becomes its argument, parenthesized
   unless primary. Refuse a parameter used more than once when its argument has
   calls, assignments or changes to a variable.
5. Delete the method with the line that follows it when that line is blank.

Arguments: `file` (the file that declares the method), `name` (`Type.method`).
Not in v1: `line` and `column` addressing, `keep_function`, inlining an instance
method, a body with several statements, and inlining a single call.

## Golden cases (written first)

- `inline_function_simple`: `tax(a)` in `return a + tax(a)` becomes `(a * 0.2)`.
- `inline_function_refused_instance`: refused, "is not static".
- `inline_function_refused_used_twice`: refused, "used 2 times" (the argument
  `next()` calls a method).

## Notes

- `hand:` the golden cases' `in/` and `out/` files, written with `Write` (the tool
  is the thing being built).

## Measured

Not measured through the server: the session's tools are the build from before this
branch. The goldens and the full `verify` were run with `./mvnw`.

## Assessment

- **Through the tools or around them:** the implementation and the golden cases were
  written by hand (`Write`). The tool is the thing being built.
- **Plan held:** the operations are those named above, in `InlineFunction`. `MemberMove.member`
  found the method, `MemberMove.gate` applied the edits under the gate, and the parameter rule
  used `ExtractVariable.hasEffect`.
- **What went wrong on the way:** the first run substituted the whole `return ...;` statement
  into the call, not only its expression, and the gate refused with a syntax error. The fix was
  to take the body's range from the expression in value mode. The removal also left the blank
  line after the method, because the blank-line test rejected an empty line; fixed.
- **Also:** `ASTVisitor` has no `visit(MethodReference)`, so references are caught in
  `preVisit2`. Raw JDT lists need a typed copy under `-Xlint:all -Werror`.
- **Read:** the method, its callers and its parameters, all through the parse; no body
  read beyond the test files.

## Follow-ups

- `line` and `column` addressing; `keep_function`; instance methods (their
  receiver); a body with several statements; inlining one call at a time.
