package com.englishwordbank;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Downloads the MIT-licensed ECDICT dataset once, then imports it into SQLite. */
public final class EcdictInstaller {
    private static final URI DATASET_URL = URI.create("https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv");
    private static final long MIN_EXPECTED_BYTES = 50_000_000L;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public CompletableFuture<Integer> install(Path dataDirectory, WordRepository repository) {
        return CompletableFuture.supplyAsync(() -> {
            Path download = null;
            try {
                Files.createDirectories(dataDirectory);
                download = Files.createTempFile(dataDirectory, "ecdict-", ".csv");
                HttpRequest request = HttpRequest.newBuilder(DATASET_URL).timeout(Duration.ofMinutes(5)).GET().build();
                HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(download));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("下载英汉词典失败（HTTP " + response.statusCode() + "）。");
                }
                if (Files.size(download) < MIN_EXPECTED_BYTES) {
                    throw new IllegalStateException("下载的英汉词典文件不完整，请检查网络后重试。");
                }
                return repository.importChineseDictionary(download);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("下载英汉词典失败，请检查网络后重试。", e);
            } catch (IOException e) {
                throw new IllegalStateException("下载英汉词典失败，请检查网络后重试。", e);
            } finally {
                if (download != null) {
                    try { Files.deleteIfExists(download); } catch (IOException ignored) { }
                }
            }
        });
    }
}
