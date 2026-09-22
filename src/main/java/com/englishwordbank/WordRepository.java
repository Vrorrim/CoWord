package com.englishwordbank;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class WordRepository {
    private final String url;
    public WordRepository(Path database) { this.url = "jdbc:sqlite:" + database.toAbsolutePath(); initialize(); }
    private void initialize() {
        try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS words (text TEXT PRIMARY KEY, first_impression TEXT NOT NULL, definition TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS similar_links (left_word TEXT NOT NULL, right_word TEXT NOT NULL, score REAL NOT NULL, PRIMARY KEY (left_word, right_word))");
        } catch (SQLException e) { throw new IllegalStateException("无法初始化本地词库", e); }
    }
    public Optional<Word> find(String text) { return allWords().stream().filter(word -> word.text().equals(text)).findFirst(); }
    public List<Word> allWords() {
        List<Word> result = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(url); PreparedStatement ps = connection.prepareStatement("SELECT text, first_impression, definition FROM words ORDER BY created_at DESC")) {
            ResultSet rs = ps.executeQuery(); while (rs.next()) result.add(new Word(rs.getString(1), rs.getString(2), rs.getString(3)));
        } catch (SQLException e) { throw new IllegalStateException("无法读取本地词库", e); }
        return result;
    }
    public List<SimilarWord> addWordAndFindSimilar(Word added, double threshold) {
        List<Word> existing = allWords(); List<SimilarWord> matches = existing.stream()
                .map(word -> new SimilarWord(word, Similarity.score(added.text(), word.text())))
                .filter(match -> match.score() >= threshold).sorted(Comparator.comparingDouble(SimilarWord::score).reversed()).toList();
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setAutoCommit(false);
            try (PreparedStatement word = connection.prepareStatement("INSERT INTO words(text, first_impression, definition) VALUES (?, ?, ?)");
                 PreparedStatement link = connection.prepareStatement("INSERT OR REPLACE INTO similar_links(left_word, right_word, score) VALUES (?, ?, ?)")) {
                word.setString(1, added.text()); word.setString(2, added.firstImpression()); word.setString(3, added.definition()); word.executeUpdate();
                for (SimilarWord match : matches) { link.setString(1, added.text()); link.setString(2, match.word().text()); link.setDouble(3, match.score()); link.addBatch(); }
                link.executeBatch(); connection.commit();
            } catch (SQLException e) { connection.rollback(); throw e; }
        } catch (SQLException e) { throw new IllegalStateException("无法写入本地词库", e); }
        return matches;
    }
}
