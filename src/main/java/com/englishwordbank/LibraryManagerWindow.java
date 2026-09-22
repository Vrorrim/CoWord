package com.englishwordbank;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Dedicated, deliberate deletion area for the personal word bank. */
public final class LibraryManagerWindow {
    private final WordRepository repository;
    private final Runnable onChanged;
    private final ObservableList<Word> words = FXCollections.observableArrayList();
    private final Set<String> selected = new LinkedHashSet<>();
    private final ListView<Word> list = new ListView<>(words);
    private final Label count = new Label();
    private final Button deleteSelected = new Button();

    public LibraryManagerWindow(WordRepository repository, Runnable onChanged) {
        this.repository = repository;
        this.onChanged = onChanged;
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("English Word Bank · 词库管理");

        Label title = new Label("词库管理");
        title.getStyleClass().add("study-title");
        Label hint = new Label("可直接删除单词，或勾选多个单词后批量删除。删除会同步清理形近关联与个人记录。");
        hint.getStyleClass().add("muted");
        VBox heading = new VBox(4, title, hint);

        Button selectAll = new Button("全选");
        selectAll.setOnAction(event -> { words.forEach(word -> selected.add(word.text())); refresh(); });
        Button clearSelection = new Button("取消选择");
        clearSelection.setOnAction(event -> { selected.clear(); refresh(); });
        deleteSelected.getStyleClass().add("batch-delete-button");
        deleteSelected.setOnAction(event -> deleteMultiple());
        HBox actions = new HBox(8, count, new Region(), selectAll, clearSelection, deleteSelected);
        HBox.setHgrow(actions.getChildren().get(1), Priority.ALWAYS);
        actions.setAlignment(Pos.CENTER_LEFT);

        list.setPlaceholder(new Label("词库为空。"));
        list.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(Word word, boolean empty) {
                super.updateItem(word, empty);
                if (empty || word == null) { setGraphic(null); return; }
                CheckBox check = new CheckBox();
                check.setSelected(selected.contains(word.text()));
                check.selectedProperty().addListener((obs, old, checked) -> {
                    if (checked) selected.add(word.text()); else selected.remove(word.text());
                    updateDeleteButton();
                });
                Label wordText = new Label(word.text());
                wordText.getStyleClass().add("word-title");
                Label detail = new Label(word.definition().replace('\n', ' '));
                detail.getStyleClass().add("definition");
                detail.setMaxWidth(390);
                detail.setWrapText(true);
                VBox info = new VBox(3, wordText, detail);
                Button delete = new Button("删除");
                delete.getStyleClass().add("manager-delete-button");
                delete.setOnAction(event -> deleteOne(word));
                HBox row = new HBox(12, check, info, new Region(), delete);
                HBox.setHgrow(row.getChildren().get(2), Priority.ALWAYS);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("manager-row");
                setGraphic(row);
            }
        });

        VBox root = new VBox(15, heading, actions, list);
        root.setPadding(new Insets(22));
        root.getStyleClass().add("manager-root");
        VBox.setVgrow(list, Priority.ALWAYS);
        Scene scene = new Scene(root, 650, 620);
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(560);
        stage.setMinHeight(480);
        refresh();
        stage.show();
    }

    private void refresh() {
        words.setAll(repository.allWords());
        selected.retainAll(words.stream().map(Word::text).collect(java.util.stream.Collectors.toSet()));
        count.setText("共 " + words.size() + " 个词");
        updateDeleteButton();
        list.refresh();
    }

    private void updateDeleteButton() {
        deleteSelected.setText("删除所选（" + selected.size() + "）");
        deleteSelected.setDisable(selected.isEmpty());
    }

    private void deleteOne(Word word) {
        if (!confirm("确定删除 “" + word.text() + "” 吗？")) return;
        repository.deleteWord(word.text());
        selected.remove(word.text());
        onChanged.run();
        refresh();
    }

    private void deleteMultiple() {
        List<String> targets = List.copyOf(selected);
        if (!confirm("确定删除选中的 " + targets.size() + " 个单词吗？")) return;
        repository.deleteWords(targets);
        selected.clear();
        onChanged.run();
        refresh();
    }

    private boolean confirm(String text) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text + " 相关形近关联和个人记录也会一并删除。", ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle("删除单词");
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
}
