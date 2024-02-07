package com.craftinginterpreters.lox;

import java.util.List;
// import static com.craftinginterpreters.lox.TokenType.BANG_EQUAL;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    private Environment environment = new Environment();

    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement: statements){
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    private String stringify(Object object) {
        if (object == null)
            return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }

    // evaluating literal
    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    // evaluating unary expression
    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                // check object type
                checkNumberOperand(expr.operator, right);
                return -(double) right;
        }

        // Unreachable
        return null;
    }
    // variable expression forwarding to environment to make sure the variable is defined
    @Override
    public Object visitVariableExpr(Expr.Variable expr){
        return environment.get(expr.name);
    }


    // for unary
    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double)
            return;
        throw new RuntimeError(operator, "Operand must be a number");
    }

    // for binary
    private void checkNumberOperand(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double)
            return;
        throw new RuntimeError(operator, "Operand must be numbers.");
    }

    private boolean isTruthy(Object object) {
        if (object == null)
            return false;
        if (object instanceof Boolean)
            return (boolean) object;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null)
            return true;
        if (a == null)
            return false;
        return a.equals(b);
    }

    // evaluating parentheses [grouping]
    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    // execute statement
    private void execute (Stmt stmt){
        stmt.accept(this);
    }

    // execute block statements
    void executeBlock(List<Stmt> statements, Environment environment){
        Environment previous = this.environment;
        try{
            // new environment
            this.environment = environment;

            for (Stmt statement: statements){
                execute(statement);
            }
        } 
        // restore the previous environment
        finally {
            this.environment = previous;
        }
    }

    // for block statement
    @Override
    public Void visitBlockStmt(Stmt.Block stmt){
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    // for expression statement
    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt){
        Object value = evaluate(stmt.expression);
        // Print out expression in REPL [challenge]
        System.out.println(stringify(value));  
        return null;
    }
    // for print statement's visit method
    @Override
    public Void visitPrintStmt(Stmt.Print stmt){
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    // for declaration statement
    @Override
    public Void visitVarStmt(Stmt.Var stmt){
        Object value = null;
        if (stmt.initializer != null){
            value = evaluate(stmt.initializer);
        }
        environment.define(stmt.name.lexeme, value);
        return null;
    }

    // for assignment statement
    @Override
    public Object visitAssignExpr(Expr.Assign expr){
        Object value = evaluate(expr.value);
        environment.assign(expr.name, value);
        return value;
    }

    // evaluating binary operator
    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            // Equality operator
            case BANG_EQUAL:
                if (performDifferentTypeRelation(left, right)) {
                    return !isEqual(left, right);
                }
                return performDifferentTypeOperation(expr.operator.type, left, right);

            case EQUAL_EQUAL:
                if (performDifferentTypeRelation(left, right))
                    return isEqual(left, right);
                return performDifferentTypeOperation(expr.operator.type, left, right);

            // relational/comparison
            case GREATER:
                if (performDifferentTypeRelation(left, right)) {
                    checkNumberOperand(expr.operator, left, right);
                    return (double) left > (double) right;
                }
                return performDifferentTypeOperation(expr.operator.type, left, right);

            case GREATER_EQUAL:
                if (performDifferentTypeRelation(left, right)) {
                    checkNumberOperand(expr.operator, left, right);
                    return (double) left >= (double) right;
                }
                return performDifferentTypeOperation(expr.operator.type, left, right);

            case LESS:
                if (performDifferentTypeRelation(left, right)) {
                    checkNumberOperand(expr.operator, left, right);
                    return (double) left < (double) right;
                }
                return performDifferentTypeOperation(expr.operator.type, left, right);

            case LESS_EQUAL:
                if (performDifferentTypeRelation(left, right)) {
                    checkNumberOperand(expr.operator, left, right);
                    return (double) left <= (double) right;
                }
                return performDifferentTypeOperation(expr.operator.type, left, right);

            // arithmetic
            case MINUS:
                checkNumberOperand(expr.operator, left, right);
                return (double) left - (double) right;

            // if num, add. if string, concatenate
            case PLUS:
                if (left instanceof Double && right instanceof Double)
                    return (double) left + (double) right;
                if (left instanceof String && right instanceof String)
                    return (String) left + (String) right;

                // for Double and String value, concatenate
                if ((left instanceof String && right instanceof Double))
                    return (String) left + loseDotZero(String.valueOf(right));
                if ((left instanceof Double && right instanceof String))
                    return loseDotZero(String.valueOf(left)) + (String) right;

                throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings.");

            case SLASH:
                checkNumberOperand(expr.operator, left, right);
                if ((double)right == 0){
                    throw new RuntimeError(expr.operator, "The Divisor Cannot be 0");
                }
                else return (double) left / (double) right;
                
            case STAR:
                checkNumberOperand(expr.operator, left, right);
                return (double) left * (double) right;
        }

        return null;
    }

    // ## My Contribution ##
    // To lose .0 when concatinate
    private String loseDotZero(String number) {
        if (number.endsWith(".0"))
            return number.substring(0, number.length() - 2);
        return number;
    }

    // To perform Relational Operations between String and Number [==, !=, >, >=, <,
    // <=]
    private boolean performDifferentTypeRelation(Object left, Object right) {
        return (left instanceof String && right instanceof String)
                || (left instanceof Double && right instanceof Double);
    }

    // calls operate function to perform relation operation if different types, else
    // returns false
    private boolean performDifferentTypeOperation(TokenType operator, Object left, Object right) {
        if (left instanceof String && right instanceof Double) {
            return operate(operator, (String) left, (Double) right, true);
        } else if (left instanceof Double && right instanceof String) {
            return operate(operator, (String) right, (Double) left, false);
        }
        return false;
    }

    private boolean operate(TokenType operator, String strVal, Double doubleVal, boolean precedence) {
        int sum = 0;
        for (int i = 0; i < strVal.length(); i++) {
            char currentChar = strVal.charAt(i);
            int asciiValue = (int) currentChar;
            sum += asciiValue;
        }
        switch (operator) {
            case BANG_EQUAL:
                return doubleVal != sum;

            case EQUAL_EQUAL:
                return doubleVal == sum;

            case GREATER:
                if (precedence)
                    return sum > doubleVal;
                return doubleVal > sum;

            case GREATER_EQUAL:
                if (precedence)
                    return sum >= doubleVal;
                return doubleVal >= sum;

            case LESS:
                if (precedence)
                    return sum < doubleVal;
                return doubleVal < sum;

            case LESS_EQUAL:
                if (precedence)
                    return sum <= doubleVal;
                return doubleVal <= sum;
            default:
                return false;
        }
    }
}
