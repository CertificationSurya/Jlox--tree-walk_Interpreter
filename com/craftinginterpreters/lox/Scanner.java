package com.craftinginterpreters.lox;

import java.lang.ProcessBuilder.Redirect.Type;
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

    // defining set of reserved words in a map
    private static final Map<String, TokenType> keywords;
    static {
        keywords = new HashMap<>();
        keywords.put("and", AND);
        keywords.put("class", CLASS);
        keywords.put("else", ELSE);
        keywords.put("false", FALSE);
        keywords.put("for", FOR);
        keywords.put("fun", FUN);
        keywords.put("if", IF);
        keywords.put("nil", NIL);
        keywords.put("or", OR);
        keywords.put("print", PRINT);
        keywords.put("return", RETURN);
        keywords.put("super", SUPER);
        keywords.put("this", THIS);
        keywords.put("true", TRUE);
        keywords.put("var", VAR);
        keywords.put("while", WHILE);
    }

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
                } 
                // My contribution ( multiple line comment (/*...*/) )
                else if(match('*')) {
                    try{
                        while (true) {
                            if (advance()=='*' && advance()=='/'){
                                break;
                            }
                        }
                    }
                    catch (StringIndexOutOfBoundsException e) {
                        System.err.println("> [Error: Multi-line comment wasn't bounded]!");
                    }
                }               
                else {
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
                if (isDigit(c)){
                    number();
                }
                else if (isAlpha(c)){  // first checks if 1st letter is a-z || A-Z || _
                    identifier();  // then looks for isAlpha or isAlphaNumeric i.e num or alphabets
                }
                else{
                    // if invalid character was present like @ for eg.
                    Lox.error(line, "Unexpected character.");
                }
                break;
        }
    }

    // checks identifier
    private void identifier(){
        while (isAlphaNumeric(peek())) advance();

        String text = source.substring(start, current);
        TokenType type = keywords.get(text);  // checking our reverse word map with text. And if we don't get any reserve word. It's identifier 
        if (type == null) type = IDENTIFIER;
        addToken(type);
    }

    // handle/consume number token
    private void number(){
        while (isDigit(peek())) advance();

        // looking for a fraction part
        if (peek()=='.' && isDigit(peekNext())){
            // consume the '.'
            advance();

            while(isDigit(peek())) advance();
        }

        addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
    }

    // handle/consume string token
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
 
    private char peekNext(){
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current+1);
    }

    private boolean isAlpha(char c){
        return (c >= 'a' && c<= 'z') || 
            (c >= 'A' && c<= 'Z') ||
            c == '_';
    }

    private boolean isAlphaNumeric(char c){
        return isAlpha(c) || isDigit(c);
    }

    // peek() for digits
    private boolean isDigit(char c){
        return c >= '0' && c <= '9';
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