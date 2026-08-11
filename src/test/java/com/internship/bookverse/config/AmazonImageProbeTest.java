package com.internship.bookverse.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;

class AmazonImageProbeTest {

    private static final String BASE = "https://images.amazon.com/images/P/0195153448.01.THUMBZZZ.jpg";

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    private static final String[] CSV_URLS = {
            "http://images.amazon.com/images/P/0195153448.01.THUMBZZZ.jpg",
            "http://images.amazon.com/images/P/0373055021.01.THUMBZZZ.jpg",
            "http://images.amazon.com/images/P/0812535189.01.THUMBZZZ.jpg",
            "http://images.amazon.com/images/P/031285479X.01.THUMBZZZ.jpg",
            "http://images.amazon.com/images/P/0553567470.01.THUMBZZZ.jpg"
    };

    @Test
    void probeJdkRestClient_http1only() {
        StringBuilder report = new StringBuilder("JDK RestClient HTTP/1.1 raw:");
        HttpClient hc = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        // raw java.net.http.HttpClient, no Spring
        for (String url : CSV_URLS) {
            try {
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(url))
                        .header(HttpHeaders.USER_AGENT, UA)
                        .GET().build();
                var resp = hc.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                report.append(" ").append(url.substring(url.indexOf("/P/") + 3)).append(" -> ")
                        .append(resp.statusCode()).append(" ").append(resp.body().length).append("B;");
            } catch (Exception e) {
                report.append(" ").append(url.substring(url.indexOf("/P/") + 3)).append(" -> EXC: ")
                        .append(e.getMessage(), 0, Math.min(60, e.getMessage().length())).append(";");
            }
        }
        System.out.println("PROBE-RESULT: " + report);
    }
}