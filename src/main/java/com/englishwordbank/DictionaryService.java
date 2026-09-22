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
    private static final URI API_ROOT = URI.create("https://api.dictionaryapi.dev/api/v2/entries/en/");
    private static final int MAX_SENSES = 5;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public CompletableFuture<DictionaryResult> lookup(String word) {
        String encoded = URLEncoder.encode(word.trim().toLowerCase(), StandardCharsets.UTF_8).replace("+", "%20");
        HttpRequest request = HttpRequest.newBuilder(API_ROOT.resolve(encoded))
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseResponse);
    }

    private DictionaryResult parseResponse(HttpResponse<String> response) {
        if (response.statusCode() == 404) throw new DictionaryException("未在词典中找到这个词，请检查拼写或手动填写释义。");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new DictionaryException("词典服务暂时不可用（HTTP " + response.statusCode() + "）。");
        }
        try {
            JsonNode root = mapper.readTree(response.body());
            if (!root.isArray() || root.isEmpty()) throw new DictionaryException("词典返回的数据格式不完整。");
            JsonNode entry = root.get(0);
            String phonetic = firstText(entry, "phonetic");
            if (phonetic.isBlank()) {
                for (JsonNode item : entry.path("phonetics")) {
                    phonetic = firstText(item, "text");
                    if (!phonetic.isBlank()) break;
                }
            }

            StringBuilder definition = new StringBuilder();
            int senseCount = 0;
            for (JsonNode meaning : entry.path("meanings")) {
                String partOfSpeech = firstText(meaning, "partOfSpeech");
                for (JsonNode sense : meaning.path("definitions")) {
                    String text = firstText(sense, "definition");
                    if (text.isBlank()) continue;
                    if (!definition.isEmpty()) definition.append('\n');
                    definition.append(partOfSpeech.isBlank() ? "" : partOfSpeech + ". ").append(text);
                    if (++senseCount == MAX_SENSES) break;
                }
                if (senseCount == MAX_SENSES) break;
            }
            if (definition.isEmpty()) throw new DictionaryException("词典未返回可用释义。");
            return new DictionaryResult(firstText(entry, "word"), phonetic, definition.toString(), "Free Dictionary API");
        } catch (IOException e) {
            throw new DictionaryException("无法解析词典返回的数据。", e);
        }
    }

    private static String firstText(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isTextual() ? value.asText().trim() : "";
    }

    public record DictionaryResult(String word, String phonetic, String definition, String source) { }

    public static final class DictionaryException extends RuntimeException {
        public DictionaryException(String message) { super(message); }
        public DictionaryException(String message, Throwable cause) { super(message, cause); }
    }
}
