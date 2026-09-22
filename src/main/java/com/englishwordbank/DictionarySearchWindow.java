package com.englishwordbank;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/** Search the installed English-Chinese dictionary and add a selected result directly to the personal bank. */
public final class DictionarySearchWindow {
    private final WordRepository repository;
    private final String query;
    private final Runnable onAdded;

    public DictionarySearchWindow(WordRepository repository, String query, Runnable onAdded) {
        this.repository = repository;
        this.query = query;
        this.onAdded = onAdded;
    }

    public void show() {
        List<WordRepository.LocalDictionaryEntry> results = repository.searchChineseDictionary(query);
        Stage stage = new Stage();
        stage.setTitle("词典搜索 · " + query);
        Label title = new Label("词典搜索：" + query);
        title.getStyleClass().add("study-title");
        Label hint = new Label(results.isEmpty() ? "没有找到对应词条。请换一个关键词，或使用右上角 + 手动添加。" : "选择一个词条，直接加入你的词库。" );
        hint.getStyleClass().add("muted");

        ListView<WordRepository.LocalDictionaryEntry> list = new ListView<>(FXCollections.observableArrayList(results));
        list.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(WordRepository.LocalDictionaryEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) { setGraphic(null); return; }
                Label word = new Label(entry.word());
                word.getStyleClass().add("word-title");
                Label phonetic = new Label(entry.phonetic().isBlank() ? "" : "/" + entry.phonetic() + "/");
                phonetic.getStyleClass().add("muted");
                Label translation = new Label(entry.translation().replace('\n', ' '));
                translation.getStyleClass().add("definition");
                translation.setWrapText(true);
                translation.setMaxWidth(380);
                Button add = new Button(repository.find(entry.word()).isPresent() ? "已在词库" : "加入词库");
                add.getStyleClass().add("direct-add-button");
                add.setDisable(repository.find(entry.word()).isPresent());
                add.setOnAction(event -> addEntry(entry, add));
                HBox name = new HBox(9, word, phonetic);
                VBox info = new VBox(4, name, translation);
                Region spacer = new Region();
                HBox row = new HBox(12, info, spacer, add);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("dictionary-result-row");
                setGraphic(row);
            }
        });
        VBox root = new VBox(14, title, hint, list);
        root.setPadding(new Insets(22));
        root.getStyleClass().add("manager-root");
        VBox.setVgrow(list, Priority.ALWAYS);
        Scene scene = new Scene(root, 650, 560);
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private void addEntry(WordRepository.LocalDictionaryEntry entry, Button button) {
        String definition = "中文释义\n" + entry.translation()
                + (entry.definition().isBlank() ? "" : "\n\n英文补充释义\n" + entry.definition());
        if (!entry.phonetic().isBlank()) definition = "/" + entry.phonetic() + "/\n" + definition;
        repository.addWordAndFindSimilar(new Word(entry.word(), "", definition, Word.Mastery.NEW), 0.78);
        button.setText("已加入");
        button.setDisable(true);
        onAdded.run();
    }
}
