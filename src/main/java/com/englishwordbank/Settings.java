package com.englishwordbank;

import java.io.*;
import java.nio.file.*;
import java.util.Optional;
import java.util.Properties;

public final class Settings {
    private static final Path FILE = Path.of(System.getProperty("user.home"), ".english-word-bank", "settings.properties");
    private Settings() { }
    public static Optional<Path> loadDataDirectory() {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) { properties.load(in); return Optional.ofNullable(properties.getProperty("dataDirectory")).map(Path::of); }
        catch (IOException ignored) { return Optional.empty(); }
    }
    public static void saveDataDirectory(Path directory) {
        try { Files.createDirectories(FILE.getParent()); Properties properties = new Properties(); properties.setProperty("dataDirectory", directory.toString()); try (OutputStream out = Files.newOutputStream(FILE)) { properties.store(out, "English Word Bank local settings"); } }
        catch (IOException e) { throw new IllegalStateException("无法保存应用设置", e); }
    }
}
