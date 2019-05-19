package br.eti.sca.sampleproperties.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.annotation.RequestScope;

@RestController
@RequestScope
public class HelloController {

    @Autowired
    private Environment env;

    @Value("${message}")
    private String message; // Works only IF restcontroller has request scope

    @GetMapping
    public String getNewMessage() {
        return String.format("From env: %s, from @value: %s", env.getProperty("message"), message);
    }

}