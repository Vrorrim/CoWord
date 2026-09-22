package com.englishwordbank;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/** Detailed learning context: dictionary material, personal memory, and spelling relations. */
public final class WordDetailWindow {
    private final WordRepository repository;
    private final Word word;

    public WordDetailWindow(WordRepository repository, Word word) {
        this.repository = repository;
        this.word = word;
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("English Word Bank · " + word.text());
        Label title = new Label(word.text());
        title.getStyleClass().add("detail-word");
        VBox root = new VBox(12,
                title,
                section("词典资料"), text(word.definition(), "detail-text"),
                section("你的第一认知"), text(word.firstImpression().isBlank() ? "尚未记录。" : word.firstImpression(), "detail-memory"),
                section("形近联想"), text(formatRelations(repository.similarWordsFor(word.text())), "detail-text"));
        root.setPadding(new Insets(24));
        root.getStyleClass().add("detail-root");
        Scene scene = new Scene(root, 560, 520);
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private static Label section(String value) { Label label = new Label(value); label.getStyleClass().add("detail-section"); return label; }
    private static Label text(String value, String css) { Label label = new Label(value); label.setWrapText(true); label.getStyleClass().add(css); return label; }
    private static String formatRelations(List<SimilarWord> relations) {
        if (relations.isEmpty()) return "暂未发现形近关联。随着词库增加，系统会自动补充。";
        StringBuilder result = new StringBuilder();
        for (SimilarWord relation : relations) {
            if (!result.isEmpty()) result.append("\n\n");
            result.append(relation.word().text()).append("  ·  形近度 ")
                    .append(String.format("%.0f%%", relation.score() * 100)).append("\n")
                    .append(relation.word().firstImpression().isBlank() ? "可在词库中补充它的个人记忆。" : "对方的第一认知：" + relation.word().firstImpression());
        }
        return result.toString();
    }
}
