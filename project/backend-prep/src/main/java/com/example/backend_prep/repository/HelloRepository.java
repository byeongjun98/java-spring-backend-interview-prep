package com.example.backend_prep.repository;

import org.springframework.stereotype.Repository;

@Repository
public class HelloRepository {
    public String findHello(String name) {
        return "Hello, " + name;
    }

    public String findNice(String name) {
        return "Nice, " + name;
    }
}
