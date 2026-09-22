package com.englishwordbank;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS words (text TEXT PRIMARY KEY, first_impression TEXT NOT NULL, definition TEXT NOT NULL, mastery TEXT NOT NULL DEFAULT 'NEW', sort_order INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            ensureColumn(connection, statement, "mastery", "TEXT NOT NULL DEFAULT 'NEW'");
            ensureColumn(connection, statement, "sort_order", "INTEGER NOT NULL DEFAULT 0");
            statement.executeUpdate("UPDATE words SET sort_order = rowid WHERE sort_order = 0");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS similar_links (left_word TEXT NOT NULL, right_word TEXT NOT NULL, score REAL NOT NULL, PRIMARY KEY (left_word, right_word))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS dictionary_entries (word TEXT PRIMARY KEY, phonetic TEXT NOT NULL, translation TEXT NOT NULL, definition TEXT NOT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_dictionary_entries_word ON dictionary_entries(word)");
        } catch (SQLException e) { throw new IllegalStateException("无法初始化本地词库", e); }
    }
    private void ensureColumn(Connection connection, Statement statement, String columnName, String definition) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, "words", columnName)) {
            if (!columns.next()) statement.executeUpdate("ALTER TABLE words ADD COLUMN " + columnName + " " + definition);
        }
    }
    public Optional<Word> find(String text) { return allWords().stream().filter(word -> word.text().equals(text)).findFirst(); }
    public List<Word> allWords() {
        List<Word> result = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(url); PreparedStatement ps = connection.prepareStatement("SELECT text, first_impression, definition, mastery FROM words ORDER BY sort_order ASC, created_at DESC")) {
            ResultSet rs = ps.executeQuery(); while (rs.next()) result.add(new Word(rs.getString(1), rs.getString(2), rs.getString(3), Word.Mastery.fromDatabase(rs.getString(4))));
        } catch (SQLException e) { throw new IllegalStateException("无法读取本地词库", e); }
        return result;
    }
    public List<SimilarWord> addWordAndFindSimilar(Word added, double threshold) {
        List<Word> existing = allWords(); List<SimilarWord> matches = existing.stream()
                .map(word -> new SimilarWord(word, Similarity.score(added.text(), word.text())))
                .filter(match -> match.score() >= threshold).sorted(Comparator.comparingDouble(SimilarWord::score).reversed()).toList();
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setAutoCommit(false);
            try (PreparedStatement word = connection.prepareStatement("INSERT INTO words(text, first_impression, definition, mastery, sort_order) VALUES (?, ?, ?, ?, ?)");
                 PreparedStatement link = connection.prepareStatement("INSERT OR REPLACE INTO similar_links(left_word, right_word, score) VALUES (?, ?, ?)")) {
                word.setString(1, added.text()); word.setString(2, added.firstImpression()); word.setString(3, added.definition()); word.setString(4, added.mastery().name()); word.setInt(5, nextSortOrder(connection)); word.executeUpdate();
                for (SimilarWord match : matches) { link.setString(1, added.text()); link.setString(2, match.word().text()); link.setDouble(3, match.score()); link.addBatch(); }
                link.executeBatch(); connection.commit();
            } catch (SQLException e) { connection.rollback(); throw e; }
        } catch (SQLException e) { throw new IllegalStateException("无法写入本地词库", e); }
        return matches;
    }

    public void updateMastery(String word, Word.Mastery mastery) {
        try (Connection connection = DriverManager.getConnection(url);
             PreparedStatement statement = connection.prepareStatement("UPDATE words SET mastery = ? WHERE text = ?")) {
            statement.setString(1, mastery.name());
            statement.setString(2, word);
            statement.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("无法更新单词掌握度", e); }
    }

    public void deleteWord(String word) {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setAutoCommit(false);
            try (PreparedStatement links = connection.prepareStatement("DELETE FROM similar_links WHERE left_word = ? OR right_word = ?");
                 PreparedStatement entry = connection.prepareStatement("DELETE FROM words WHERE text = ?")) {
                links.setString(1, word); links.setString(2, word); links.executeUpdate();
                entry.setString(1, word); entry.executeUpdate();
                connection.commit();
            } catch (SQLException e) { connection.rollback(); throw e; }
        } catch (SQLException e) { throw new IllegalStateException("无法删除单词", e); }
    }

    public void deleteWords(List<String> words) {
        if (words.isEmpty()) return;
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setAutoCommit(false);
            try (PreparedStatement links = connection.prepareStatement("DELETE FROM similar_links WHERE left_word = ? OR right_word = ?");
                 PreparedStatement entry = connection.prepareStatement("DELETE FROM words WHERE text = ?")) {
                for (String word : words) {
                    links.setString(1, word); links.setString(2, word); links.addBatch();
                    entry.setString(1, word); entry.addBatch();
                }
                links.executeBatch();
                entry.executeBatch();
                connection.commit();
            } catch (SQLException e) { connection.rollback(); throw e; }
        } catch (SQLException e) { throw new IllegalStateException("无法批量删除单词", e); }
    }

    public void reorderWords(List<Word> words) {
        try (Connection connection = DriverManager.getConnection(url);
             PreparedStatement statement = connection.prepareStatement("UPDATE words SET sort_order = ? WHERE text = ?")) {
            connection.setAutoCommit(false);
            try {
                int order = 1;
                for (Word word : words) {
                    statement.setInt(1, order++);
                    statement.setString(2, word.text());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException e) { connection.rollback(); throw e; }
        } catch (SQLException e) { throw new IllegalStateException("无法保存词库排序", e); }
    }

    private int nextSortOrder(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM words")) {
            return result.next() ? result.getInt(1) : 1;
        }
    }

    public Optional<LocalDictionaryEntry> findChineseDictionaryEntry(String word) {
        try (Connection connection = DriverManager.getConnection(url);
             PreparedStatement statement = connection.prepareStatement("SELECT word, phonetic, translation, definition FROM dictionary_entries WHERE word = ?")) {
            statement.setString(1, word.trim().toLowerCase());
            ResultSet result = statement.executeQuery();
            return result.next() ? Optional.of(new LocalDictionaryEntry(result.getString(1), result.getString(2), result.getString(3), result.getString(4))) : Optional.empty();
        } catch (SQLException e) { throw new IllegalStateException("无法读取离线英汉词典", e); }
    }

    public boolean hasChineseDictionary() {
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT EXISTS(SELECT 1 FROM dictionary_entries LIMIT 1)")) {
            return result.next() && result.getInt(1) == 1;
        } catch (SQLException e) { throw new IllegalStateException("无法检查离线英汉词典", e); }
    }

    public int importChineseDictionary(Path csvFile) {
        String insert = "INSERT OR REPLACE INTO dictionary_entries(word, phonetic, translation, definition) VALUES (?, ?, ?, ?)";
        try (BufferedReader reader = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8);
             CSVParser csv = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader);
             Connection connection = DriverManager.getConnection(url);
             PreparedStatement statement = connection.prepareStatement(insert)) {
            connection.setAutoCommit(false);
            try (Statement clear = connection.createStatement()) { clear.executeUpdate("DELETE FROM dictionary_entries"); }
            int count = 0;
            for (CSVRecord record : csv) {
                String word = record.get("word").trim().toLowerCase();
                String translation = record.get("translation").trim();
                if (word.isBlank() || translation.isBlank()) continue;
                statement.setString(1, word);
                statement.setString(2, record.get("phonetic").trim());
                statement.setString(3, translation);
                statement.setString(4, record.get("definition").trim());
                statement.addBatch();
                if (++count % 1_000 == 0) statement.executeBatch();
            }
            statement.executeBatch();
            connection.commit();
            return count;
        } catch (SQLException | IOException e) { throw new IllegalStateException("无法导入离线英汉词典", e); }
    }

    public record LocalDictionaryEntry(String word, String phonetic, String translation, String definition) { }
}
