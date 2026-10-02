package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    private final Interpreter interpreter;

    // Challenge 3: track declare/define/used separately to catch unused locals.
    private enum VarState { DECLARED, DEFINED, USED }

    // Each scope maps name -> (state, index).
    // Challenge 4: index is the variable's slot in this scope for array-based lookup.
    private static class VarInfo {
        VarState state;
        final int index;
        final Token token;
        VarInfo(VarState state, int index, Token token) {
            this.state = state;
            this.index = index;
            this.token = token;
        }
    }

    private final Stack<Map<String, VarInfo>> scopes = new Stack<>();
    private FunctionType currentFunction = FunctionType.NONE;

    private enum FunctionType { NONE, FUNCTION }

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if (!scopes.isEmpty()) {
            VarInfo info = scopes.peek().get(expr.name.lexeme);
            if (info != null && info.state == VarState.DECLARED) {
                Lox.error(expr.name, "Can't read local variable in its own initializer.");
            }
        }
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);
        resolveFunction(stmt.params, stmt.body, FunctionType.FUNCTION);
        return null;
    }

    // Challenge 2: resolve a lambda expression -- same as a function but no name.
    @Override
    public Void visitLambdaExpr(Expr.Lambda expr) {
        resolveFunction(expr.params, expr.body, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        }
        if (stmt.value != null) {
            resolve(stmt.value);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);
        for (Expr argument : expr.arguments) {
            resolve(argument);
        }
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    private void resolve(Stmt stmt) { stmt.accept(this); }
    private void resolve(Expr expr) { expr.accept(this); }

    private void beginScope() {
        scopes.push(new HashMap<>());
    }

    // Challenge 3: at scope exit, warn about declared-but-never-used locals.
    private void endScope() {
        Map<String, VarInfo> scope = scopes.pop();
        for (Map.Entry<String, VarInfo> entry : scope.entrySet()) {
            if (entry.getValue().state == VarState.DEFINED) {
                Lox.error(entry.getValue().token,
                    "Local variable '" + entry.getKey() + "' is never used.");
            }
        }
    }

    private void declare(Token name) {
        if (scopes.isEmpty()) return;
        Map<String, VarInfo> scope = scopes.peek();
        if (scope.containsKey(name.lexeme)) {
            Lox.error(name, "Already a variable with this name in this scope.");
        }
        int index = scope.size();
        scope.put(name.lexeme, new VarInfo(VarState.DECLARED, index, name));
    }

    private void define(Token name) {
        if (scopes.isEmpty()) return;
        VarInfo info = scopes.peek().get(name.lexeme);
        if (info != null) info.state = VarState.DEFINED;
    }

    private void resolveLocal(Expr expr, Token name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            VarInfo info = scopes.get(i).get(name.lexeme);
            if (info != null) {
                info.state = VarState.USED;
                // Challenge 4: pass both depth and index to the interpreter.
                interpreter.resolve(expr, scopes.size() - 1 - i, info.index);
                return;
            }
        }
    }

    private void resolveFunction(List<Token> params, List<Stmt> body, FunctionType type) {
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;
        beginScope();
        for (Token param : params) {
            declare(param);
            define(param);
            // mark params as used so they don't trigger the unused-variable warning
            scopes.peek().get(param.lexeme).state = VarState.USED;
        }
        resolve(body);
        endScope();
        currentFunction = enclosingFunction;
    }
}
