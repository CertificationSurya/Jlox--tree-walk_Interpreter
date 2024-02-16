package com.craftinginterpreters.lox;

import java.io.Console;
import java.util.ArrayList;
import java.util.Map;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;


class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    private boolean isPrompt = false;
    final Environment globals = new Environment();
    private Environment environment = globals;
    private final Map<Expr, Integer> locals = new HashMap<>();

    // stuffing native function in the global scope
    Interpreter() {
        globals.define("clock", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double) System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() {
                return "<native fn>";
            }
        });

        // My Contribution for user input
        globals.define("getData", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Console console = System.console();
                String text = console.readLine();

                Pattern pattern = Pattern.compile("[^0-9.]"); // !0-9 and .
                if (pattern.matcher(text).find()) { // if input text found any that's not 0-9 and . then return as
                                                    // String
                    return text;
                }
                // else return as Double;
                else
                    return Double.parseDouble(loseDotZero(text));
            }

            @Override
            public String toString() {
                return "<native fn>";
            }
        });
    }

    void interpret(List<Stmt> statements, boolean prompt) {
        if(prompt) isPrompt = prompt;
        try {
            for (Stmt statement : statements) {
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

    // evaluating logical expression
    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left))
                return left;
        } else {
            if (!isTruthy(left))
                return left;
        }
        return evaluate(expr.right);
    }
    
    // evaluating set operation of class
    @Override
    public Object visitSetExpr (Expr.Set expr){
        Object object = evaluate(expr.object);

        if (!(object instanceof LoxInstance)) {
            throw new RuntimeError(expr.name, "Only instances have fields");
        }

        Object value = evaluate(expr.value);
        ((LoxInstance)object).set(expr.name, value);
        return value;
    }

    @Override  
    public Object visitSuperExpr (Expr.Super expr){
        int distance = locals.get(expr);
        LoxClass superclass = (LoxClass)environment.getAt(distance, "super");

        LoxInstance object = (LoxInstance) environment.getAt(distance -1, "this");
        LoxFunction method = superclass.findMethod(expr.method.lexeme);

        if (method == null){
            throw new RuntimeError(expr.method, "undefined property '" + expr.method.lexeme+"' .");
        }

        return method.bind(object);
    }

    // interpreting 'this' expression
    @Override
    public Object visitThisExpr(Expr.This expr){
        return lookUpVariable(expr.keyword, expr);
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

    // variable expression forwarding to environment to make sure the variable is
    // defined
    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    // accessing resolved variable
    private Object lookUpVariable(Token name, Expr expr){
        Integer distance = locals.get(expr);
        if (distance != null){
            return environment.getAt(distance, name.lexeme);
        }
        else {
            return globals.get(name);
        }
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
    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    // Resolver hands the number (depth between variable found scope and current scope)
    void resolve (Expr expr, int depth){
        locals.put(expr, depth);
    }

    // execute block statements
    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            // new environment
            this.environment = environment;

            for (Stmt statement : statements) {
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
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    // for classes
    @Override
    public Void visitClassStmt(Stmt.Class stmt){
        Object superclass = null;
        if (stmt.superclass != null) {
            superclass = evaluate(stmt.superclass);
            if (!(superclass instanceof LoxClass)){
                throw new RuntimeError(stmt.superclass.name, "Superclass must be a class.");
            }
        }

        environment.define(stmt.name.lexeme, null);

        if (stmt.superclass != null){
            environment = new Environment();
            environment.define("super", superclass);
        }

        Map<String, LoxFunction> methods = new HashMap<>();
        for (Stmt.Function method : stmt.methods){
            LoxFunction function = new LoxFunction(method, environment, method.name.lexeme.equals("init"));
            methods.put(method.name.lexeme, function);
        }

        LoxClass klass = new LoxClass(stmt.name.lexeme,(LoxClass) superclass, methods);
        
        if (superclass != null){
            environment = environment.enclosing;
        }

        environment.assign(stmt.name, klass);
        return null;
    }

    // for expression statement
    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        Object value = evaluate(stmt.expression);
        // Print out expression in REPL [challenge]
        if (stmt.display && isPrompt) {
            System.out.println(stringify(value));
        }
        return null;
    }

    // for function blocks
    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        // here environment is the active environment when the function is declared not
        // when it's called
        LoxFunction function = new LoxFunction(stmt, environment, false);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    // for function call
    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr arguement : expr.arguments) {
            arguments.add(evaluate(arguement));
        }

        // if not callable callee
        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren, "Can only call functions and classes");
        }

        LoxCallable function = (LoxCallable) callee;

        // checks if arity (function parameter) matches arguement
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren,
                    "Expected " + function.arity() + " arguments but got " + arguments.size() + ".");
        }
        return function.call(this, arguments);
    }

    @Override
    public Object visitGetExpr(Expr.Get expr){
        Object object = evaluate(expr.object);
        if (object instanceof LoxInstance){
            return ((LoxInstance) object).get(expr.name);
        }
        throw new RuntimeError(expr.name, "Only instances have properties");
    }

    // for if-else
    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    // for print statement's visit method
    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    // for returning the function
    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null)
            value = evaluate(stmt.value);
        throw new Return(value);
    }

    // for declaration statement
    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }
        environment.define(stmt.name.lexeme, value);
        return null;
    }

    // for while loop execution
    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }
        return null;
    }

    // for assignment statement
    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);

        Integer distance = locals.get(expr);
        if (distance != null){
            environment.assignAt(distance, expr.name, value);
        }
        else {
            globals.assign(expr.name, value);
        }

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
                if ((double) right == 0) {
                    throw new RuntimeError(expr.operator, "The Divisor Cannot be 0");
                } else
                    return (double) left / (double) right;

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
