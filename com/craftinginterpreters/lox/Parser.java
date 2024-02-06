package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;
import static com.craftinginterpreters.lox.TokenType.*;

// Each grammer rule becomes a method inside this class
class Parser {
    // for throwing error
    private static class ParseError extends RuntimeException {
    }

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // method to kick off parsing
    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
    }

    // expanding expression to equality rule
    private Expr expression() {
        return assignment();
    }

    // declaration statement
    private Stmt declaration() {
        try {
            if (match(VAR))
                return varDeclaration();
            return statement();
        } catch (ParseError error) {
            synchronize(); // synchronize when parser goes to panic mode [error recovery]
            return null;
        }
    }

    // parses one of the statements
    private Stmt statement() {
        if (match(PRINT))
            return printStatement();
        // for block scope stating
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        return expressionStatement();
    }

    // print statement method
    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expected ';' after value.");
        return new Stmt.Print(value);
    }

    // variable declaration statement
    private Stmt varDeclaration(){
        Token name = consume(IDENTIFIER, "Except variable name.");

        Expr initializer = null;
        if (match(EQUAL)){
            initializer = expression();
        }
        consume(SEMICOLON, "Except ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    // other statement method
    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expected ';' after value.");
        return new Stmt.Expression(expr);
    }

    // block scope check and functionality
    private List<Stmt> block(){
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }
        
        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    // assignment method to confirm if it's equality or assignment of value
    private Expr assignment(){
        Expr expr = equality();

        if (match(EQUAL)){
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable){
                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            }
            error(equals, "Invalid assignment target");
        }
        return expr;
    }

    // rules for equality
    private Expr equality() {
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    // for comparision
    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // for term()
    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // for factor
    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // for unary
    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    // for primary
    private Expr primary() {
        if (match(FALSE))
            return new Expr.Literal(false);
        if (match(TRUE))
            return new Expr.Literal(true);
        if (match(NIL))
            return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)){
             return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        // this statement works when non of the grammer rule matches because it didn't
        // matched in the lowest precedence rule and we'll come here at last after
        // dealing with other precedence.
        throw error(peek(), "Expect expression.");
    }

    // Utilities Functions
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    // consume. looks if next token is of expected types.
    private Token consume(TokenType type, String message) {
        if (check(type))
            return advance();
        throw error(peek(), message);
    }

    // returns true if current token is of the given type. Unlike match, it never
    // consumes token.
    private boolean check(TokenType type) {
        if (isAtEnd())
            return false;
        return peek().type == type;
    }

    // consumes current token and returns it.
    private Token advance() {
        if (!isAtEnd())
            current++;
        return previous();
    }

    // checks if we've run out of tokens to parse
    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    // current tokens that's yet to consume
    private Token peek() {
        return tokens.get(current);
    }

    // most recently consumed token
    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    // method to discard tokens until we're right at the beginning of the next
    // statement.
    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON)
                return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    break;
            }
            advance();
        }
    }

}
