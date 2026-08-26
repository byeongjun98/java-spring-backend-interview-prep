package com.example.backend_prep.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend_prep.service.HelloService;

@RestController
public class HelloController {
    private final HelloService helloService;

    public HelloController(HelloService helloService) {
        this.helloService = helloService;
    }

    @GetMapping("/hello")
    public String hello(@RequestParam String name) {
        return helloService.getHello(name);
    }

    @GetMapping("/nice")
    public String nice(@RequestParam String name) {
        return helloService.getNice(name);
    }
}
