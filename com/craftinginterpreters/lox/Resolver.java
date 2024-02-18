// This class is created to resolve the possibility of conflict of mutable environment and it's impact on function on giving two different output for the same variable

package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import com.craftinginterpreters.lox.Expr.Logical;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    private final Interpreter interpreter;
    private final Stack<Map<String, Boolean>> scopes = new Stack<>();
    private FunctionType currentFunction = FunctionType.NONE;

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    private enum FunctionType {
        NONE, FUNCTION, INITIALIZER, METHOD
    }

    private enum ClassType {
        NONE, CLASS, SUBCLASS
    }

    private ClassType currentClass = ClassType.NONE;

    // For Block statement
    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt){
        ClassType enclosingClass = currentClass;
        currentClass = ClassType.CLASS;

        declare(stmt.name);
        define(stmt.name);

        if (stmt.superclass != null && stmt.name.lexeme.equals(stmt.superclass.name.lexeme)){
            Lox.error(stmt.superclass.name, "A class can't inherit from itself.");
        }
        
        if (stmt.superclass != null){
            currentClass = ClassType.SUBCLASS;
            resolve(stmt.superclass);
        }

        if (stmt.superclass != null){
            beginScope();
            scopes.peek().put("super", true);
        }

        beginScope();
        scopes.peek().put("this", true);

        for (Stmt.Function method : stmt.methods){
            FunctionType declaration = FunctionType.METHOD;
            if (method.name.lexeme.equals("init")){
                declaration = FunctionType.INITIALIZER;
            }

            resolveFunction(method, declaration);
        }

        endScope();

        if (stmt.superclass != null) endScope();

        currentClass = enclosingClass;

        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    // Resolving function declarations && function body
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);

        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null)
            resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        // check if the return statement is in function or in global
        if (currentFunction == FunctionType.NONE){
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        }

        if (stmt.value != null) {
            if (currentFunction == FunctionType.INITIALIZER){
                Lox.error(stmt.keyword, "Can't return a value from an initializer.");
            }
            
            resolve(stmt.value);
        }

        return null;
    }

    // for resolving variable
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
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }

    // Resolving assignment expression
    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }
    @Override
    public Void visitTernaryExpr(Expr.Ternary expr) {
        resolve(expr.condition);
        resolve(expr.trueCase);
        resolve(expr.falseCase);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);

        for (Expr arguement : expr.arguments) {
            resolve(arguement);
        }

        return null;
    }

    // the resolution recurse only expression to the left of the dot
    @Override
    public Void visitGetExpr(Expr.Get expr){
        resolve(expr.object);
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

    // setter for class
    @Override
    public Void visitSetExpr(Expr.Set expr){
        resolve(expr.value);  // object value being set
        resolve(expr.object); // object whose property is being set
        return null;
    }

    @Override
    public Void visitSuperExpr(Expr.Super expr){
        if (currentClass == ClassType.NONE){
            Lox.error(expr.keyword, "Can't use 'super' outside of a class");
        }
        else if (currentClass != ClassType.SUBCLASS){
            Lox.error(expr.keyword, "Can't use 'super' in a class with no superclass.");
        }
        resolveLocal(expr, expr.keyword);
        return null;
    }

    // this keyword of class
    @Override
    public Void visitThisExpr(Expr.This expr){
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Can't use 'this' outside of a class");
            return null;
        }

        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        // if stack holding block is not empty,
        if (!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) {
            Lox.error(expr.name, "Can't read local variable in its own initializer.");
        }

        resolveLocal(expr, expr.name);
        return null;
    }

    // for resolving statement
    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    // for resolving expression
    private void resolve(Expr expr) {
        expr.accept(this);
    }

    void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    // function body resolve
    private void resolveFunction(Stmt.Function function, FunctionType type) {
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;

        beginScope();
        for (Token param : function.params) {
            declare(param);
            define(param);
        }
        resolve(function.body);
        endScope();

        currentFunction = enclosingFunction;  // for nested function
    }

    // new block scope is created like
    private void beginScope() {
        scopes.push(new HashMap<String, Boolean>());
    }

    // exiting one scope
    private void endScope() {
        scopes.pop();
    }

    // two step binding to know if the expression is inside of initializer
    private void declare(Token name) {
        if (scopes.isEmpty())
            return;

        Map<String, Boolean> scope = scopes.peek();
        if (scope.containsKey(name.lexeme)){
            Lox.error(name, "Already a variable with this name in this scope");
        }
        scope.put(name.lexeme, false); // value false to state that variable exists but is unavailable
    }

    private void define(Token name) {
        if (scopes.isEmpty())
            return;
        scopes.peek().put(name.lexeme, true); // marking variable as fully initialized and available for user.
    }

    // resolving Local scope & variable
    private void resolveLocal(Expr expr, Token name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size() - 1 - i);
                return;
            }
        }
    }

}