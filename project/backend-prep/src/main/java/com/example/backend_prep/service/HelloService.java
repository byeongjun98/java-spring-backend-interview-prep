package com.example.backend_prep.service;

import org.springframework.stereotype.Service;

import com.example.backend_prep.repository.HelloRepository;

@Service
public class HelloService {
    private final HelloRepository helloRepository;

    public HelloService(HelloRepository helloRepository) {
        this.helloRepository = helloRepository;
    }

    public String getHello(String name) {
        return helloRepository.findHello(name);
    }

    public String getNice(String name) {
        return helloRepository.findNice(name);
    }
}
