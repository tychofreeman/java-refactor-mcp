package p;

sealed interface Expr<T> permits Calc.Lit, Calc.Neg {}
