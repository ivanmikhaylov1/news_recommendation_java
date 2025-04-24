package com.example.demo.parser;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
public abstract class HttpParser {

    protected static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    protected static final int TIMEOUT = 20000;

    private static final HttpClient client = HttpClient.newHttpClient();

    protected CompletableFuture<Optional<Document>> getPage(String link) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(link))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofMillis(TIMEOUT))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        return Optional.of(Jsoup.parse(response.body(), link));
                    } catch (Exception e) {
                        log.error("Ошибка формата страницы: {}", link, e);
                        return Optional.<Document>empty();
                    }
                })
                .exceptionally(e -> {
                    log.error("Ошибка при запросе страницы: {}", link, e);
                    return Optional.empty();
                });
    }
}
