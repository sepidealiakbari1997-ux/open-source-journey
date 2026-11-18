package com.pipita.oteldemo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @GetMapping("/hello")
    public String hello() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://postman-echo.com/get?message=hello"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> first =
            client.send(request, HttpResponse.BodyHandlers.ofString());

        HttpResponse<String> second =
            client.send(request, HttpResponse.BodyHandlers.ofString());

        return second.body();
    }
}