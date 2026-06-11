package com.university.microservices.broker.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/mail")
public class MailController {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${MAIL_API_HOST:mailhog}")
    private String mailHost;

    @GetMapping("/messages")
    public Object getMessages() {
        try {
            // MailHog API is on port 8025
            String url = "http://" + mailHost + ":8025/api/v2/messages";
            return restTemplate.getForObject(url, Object.class);
        } catch (Exception e) {
            return "{\"items\": []}";
        }
    }
}
