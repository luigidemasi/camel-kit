package com.example;

public class OrderProcessor {

    public String validate(String body) {
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("Empty order");
        }
        return body;
    }

    public String transform(String body) {
        return body.toUpperCase();
    }
}
