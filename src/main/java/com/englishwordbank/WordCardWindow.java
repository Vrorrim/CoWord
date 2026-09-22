package com.englishwordbank;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/** A focused, original flashcard study view for the user's personal word bank. */
public final class WordCardWindow {
    private final WordRepository repository;
    private final List<Word> words;
    private final Runnable onChanged;
    private final Label progress = new Label();
    private final Label state = new Label();
    private final Label wordText = new Label();
    private final Label prompt = new Label();
    private final Label definition = new Label();
    private final Label memoryHint = new Label();
    private final StackPane card = new StackPane();
    private boolean flipped;
    private int index;
    private Stage stage;

    public WordCardWindow(WordRepository repository, List<Word> words, Runnable onChanged) {
        this.repository = repository;
        this.words = new ArrayList<>(words);
        this.onChanged = onChanged;
    }

    public void show() {
        stage = new Stage();
        stage.setTitle("English Word Bank · 词卡学习");
        BorderPane root = new BorderPane();
        root.getStyleClass().add("study-root");
        root.setTop(topBar());
        root.setCenter(cardArea());
        root.setBottom(gradeBar());

        Scene scene = new Scene(root, 720, 700);
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SPACE) flip();
            else if (event.getCode() == KeyCode.RIGHT) next();
            else if (event.getCode() == KeyCode.LEFT) previous();
        });
        stage.setMinWidth(620);
        stage.setMinHeight(600);
        stage.setScene(scene);
        render();
        stage.show();
    }

    private HBox topBar() {
        Label title = new Label("今日词卡");
        title.getStyleClass().add("study-title");
        Button previous = new Button("← 上一张");
        previous.setOnAction(event -> previous());
        Button next = new Button("下一张 →");
        next.setOnAction(event -> next());
        HBox box = new HBox(10, title, new Region(), progress, previous, next);
        HBox.setHgrow(box.getChildren().get(1), javafx.scene.layout.Priority.ALWAYS);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("study-top-bar");
        return box;
    }

    private StackPane cardArea() {
        wordText.getStyleClass().add("flash-word");
        state.getStyleClass().add("flash-state");
        prompt.getStyleClass().add("flash-prompt");
        definition.getStyleClass().add("flash-definition");
        definition.setWrapText(true);
        definition.setMaxWidth(470);
        memoryHint.getStyleClass().add("flash-memory");
        memoryHint.setWrapText(true);
        memoryHint.setMaxWidth(470);

        VBox content = new VBox(16, state, wordText, prompt, definition, memoryHint);
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(510);
        card.getChildren().add(content);
        card.setMaxWidth(560);
        card.setMinHeight(400);
        card.getStyleClass().add("flash-card");
        card.setOnMouseClicked(event -> flip());

        StackPane area = new StackPane(card);
        area.setPadding(new Insets(28, 42, 18, 42));
        return area;
    }

    private VBox gradeBar() {
        Label help = new Label("点击词卡或按空格翻转 · ← → 切换词卡");
        help.getStyleClass().add("study-help");
        Button hard = gradeButton("陌生", Word.Mastery.HARD, "grade-hard");
        Button review = gradeButton("模糊", Word.Mastery.REVIEW, "grade-review");
        Button mastered = gradeButton("掌握", Word.Mastery.MASTERED, "grade-mastered");
        HBox actions = new HBox(12, hard, review, mastered);
        actions.setAlignment(Pos.CENTER);
        VBox box = new VBox(12, help, actions);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(12, 24, 28, 24));
        return box;
    }

    private Button gradeButton(String label, Word.Mastery mastery, String styleClass) {
        Button button = new Button(label);
        button.getStyleClass().addAll("grade-button", styleClass);
        button.setOnAction(event -> grade(mastery));
        return button;
    }

    private void render() {
        Word word = words.get(index);
        progress.setText((index + 1) + " / " + words.size());
        state.setText(word.mastery().label());
        wordText.setText(word.text());
        if (flipped) {
            card.getStyleClass().add("flipped-card");
            prompt.setText("中文释义与个人记忆");
            definition.setText(word.definition());
            memoryHint.setText(word.firstImpression().isBlank() ? "还没有写下自己的第一认知。" : "你的第一认知：" + word.firstImpression());
        } else {
            card.getStyleClass().remove("flipped-card");
            prompt.setText("先在脑中回想含义，然后点击卡片翻转");
            definition.setText("");
            memoryHint.setText("");
        }
    }

    private void flip() { flipped = !flipped; render(); }
    private void next() { index = (index + 1) % words.size(); flipped = false; render(); }
    private void previous() { index = (index - 1 + words.size()) % words.size(); flipped = false; render(); }

    private void grade(Word.Mastery mastery) {
        Word current = words.get(index);
        repository.updateMastery(current.text(), mastery);
        words.set(index, new Word(current.text(), current.firstImpression(), current.definition(), mastery));
        onChanged.run();
        next();
    }
}
