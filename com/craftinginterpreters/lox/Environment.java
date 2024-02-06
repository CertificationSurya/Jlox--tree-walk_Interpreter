package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

class Environment {
    final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    // giving environment reference to its enclosing one.
    
    // for global scope
    Environment(){  
        enclosing = null;
    }
    // for new nested local scope 
    Environment (Environment enclosing){
        this.enclosing = enclosing;
    }


    Object get(Token name){
        // for lookup in current environment
        if (values.containsKey(name.lexeme)){
            return values.get(name.lexeme);
        }
        // if not, then look at enclosing/outer one
        if (enclosing != null) return enclosing.get(name);

        throw new RuntimeError(name, "Undefined variable '"+ name.lexeme + "'.");
    }
    // assign value [doesn't create new variable]
    void assign(Token name, Object value){
        if (values.containsKey(name.lexeme)){
            values.put(name.lexeme, value);
            return;
        }
        if (enclosing!= null) {
            enclosing.assign(name, value);
            return;
        }
        throw new RuntimeError(name, "Undefined variable '" + name.lexeme+ "'.");
    }


    void define(String name, Object value) {
        values.put(name, value);
    }
}
