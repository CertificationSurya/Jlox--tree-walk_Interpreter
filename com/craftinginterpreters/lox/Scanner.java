package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sound.sampled.Line;

import static com.craftinginterpreters.lox.TokenType.*;

class Scanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<>(); // tokens storage after scanning
    private int start = 0;
    private int current = 0;
    private int line = 1;

    Scanner(String source) {
        this.source = source;
    }

    // storing token
    List<Token> scanTokens() {
        while (!isAtEnd()) {
            // we are at the beginning of the next lexeme.
            start = current;
            scanToken();
        }
        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    // is the pointer at string end?  E.g: in "var ", checks if pointer is at 'r'
    private boolean isAtEnd() {
        return current >= source.length();
    }

    // add cases for recognizing Lexemes
    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(':
                addToken(LEFT_PAREN);
                break;
            case ')':
                addToken(RIGHT_PAREN);
                break;
            case '{':
                addToken(LEFT_BRACE);
                break;
            case '}':
                addToken(RIGHT_BRACE);
                break;
            case ',':
                addToken(COMMA);
                break;
            case '.':
                addToken(DOT);
                break;
            case '-':
                addToken(MINUS);
                break;
            case '+':
                addToken(PLUS);
                break;
            case ';':
                addToken(SEMICOLON);
                break;
            case '*':
                addToken(STAR);
                break;

            // operator with possible second character
            case '!':
                addToken(match('=') ? BANG_EQUAL : BANG);
                break;
            case '=':
                addToken(match('=') ? EQUAL_EQUAL : EQUAL);
                break;
            case '<':
                addToken(match('=') ? LESS_EQUAL : LESS);
                break;
            case '>':
                addToken(match('=') ? GREATER_EQUAL : GREATER);
                break;

            // longer lexeme => for /. if / division, else if //, comment so, loop till \n without adding token
            case '/':
                if (match('/')){
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else {
                    addToken(SLASH);
                }
                break;

            // Whitespaces and new lines
            case ' ':
            case '\r':
            case '\t':
                break;
            case '\n':
                line++;
                break;

            // String literals
            case'"': string(); break;

            default:
                // if invalid character was present like @ for eg.
                Lox.error(line, "Unexpected character.");
                break;
        }
    }

    // handle string token
    private void string(){
        while(peek() != '"' && !isAtEnd()){
            if (peek() == '\n') line++;  // line is incremented as Lox supports multiline string
            advance();
        }

        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
            return;
        }

        // The closing
        advance();
        // Trimming the surrounding Quote
        String value = source.substring(start + 1, current -1);
        addToken(STRING, value);
    }

    // To check if the current character of operator matches with expected ones
    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;

        current++;
        return true;
    }
    // to keep peeking the code until the end of line
    private char peek(){
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    // consumes next char in source file and returns it. 
    private char advance() {  // for input
        current++;
        return source.charAt(current - 1);
    }

    private void addToken(TokenType type) {  
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {// for output
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }
}