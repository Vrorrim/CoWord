package com.englishwordbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * External dictionary adapter.  The UI only depends on DictionaryResult, so a
 * licensed provider or local WordNet adapter can replace this source later.
 */
public final class DictionaryService {
    private static final String DATAMUSE_API = "https://api.datamuse.com/words";
    private static final int MAX_SENSES = 5;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public CompletableFuture<DictionaryResult> lookup(String word) {
        String encoded = URLEncoder.encode(word.trim().toLowerCase(), StandardCharsets.UTF_8).replace("+", "%20");
        // `sp` performs exact spelling lookup when no wildcard is supplied;
        // `md=d` asks Datamuse for its dictionary definitions.
        URI endpoint = URI.create(DATAMUSE_API + "?sp=" + encoded + "&md=d&max=1");
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseResponse);
    }

    private DictionaryResult parseResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new DictionaryException("词典服务暂时不可用（HTTP " + response.statusCode() + "）。");
        }
        try {
            JsonNode root = mapper.readTree(response.body());
            if (!root.isArray() || root.isEmpty()) throw new DictionaryException("词典返回的数据格式不完整。");
            JsonNode entry = root.get(0);

            StringBuilder definition = new StringBuilder();
            int senseCount = 0;
            for (JsonNode item : entry.path("defs")) {
                String raw = item.asText("").trim();
                int separator = raw.indexOf('\t');
                if (separator < 1 || separator == raw.length() - 1) continue;
                if (!definition.isEmpty()) definition.append('\n');
                definition.append(normalizePartOfSpeech(raw.substring(0, separator)))
                        .append(". ")
                        .append(raw.substring(separator + 1).trim());
                if (++senseCount == MAX_SENSES) break;
            }
            if (definition.isEmpty()) throw new DictionaryException("词典未返回可用释义。");
            return new DictionaryResult(firstText(entry, "word"), "", definition.toString(), "Datamuse 开放词典");
        } catch (IOException e) {
            throw new DictionaryException("无法解析词典返回的数据。", e);
        }
    }

    private static String firstText(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isTextual() ? value.asText().trim() : "";
    }

    private static String normalizePartOfSpeech(String tag) {
        return switch (tag.toLowerCase()) {
            case "n" -> "n";
            case "v" -> "v";
            case "adj" -> "adj";
            case "adv" -> "adv";
            case "prep" -> "prep";
            case "conj" -> "conj";
            default -> tag;
        };
    }

    public record DictionaryResult(String word, String phonetic, String definition, String source) { }

    public static final class DictionaryException extends RuntimeException {
        public DictionaryException(String message) { super(message); }
        public DictionaryException(String message, Throwable cause) { super(message, cause); }
    }
}
