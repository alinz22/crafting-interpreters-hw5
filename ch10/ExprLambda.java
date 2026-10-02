package com.craftinginterpreters.lox;

import java.util.List;

// Add inside abstract class Expr { ... } alongside the other node classes.
// Also add visitLambdaExpr(Lambda expr) to Expr.Visitor<R>.

abstract class ExprLambdaSnippet {

    static class Lambda extends com.craftinginterpreters.lox.Expr {
        final List<com.craftinginterpreters.lox.Token> params;
        final List<com.craftinginterpreters.lox.Stmt> body;

        Lambda(List<com.craftinginterpreters.lox.Token> params,
               List<com.craftinginterpreters.lox.Stmt> body) {
            this.params = params;
            this.body = body;
        }

        @Override
        <R> R accept(com.craftinginterpreters.lox.Expr.Visitor<R> visitor) {
            return visitor.visitLambdaExpr(this);
        }
    }
}
