package com.englishwordbank;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class App extends Application {
    private static final Map<String, String> DEMO_DICTIONARY = Map.of(
            "deposit", "v. 存放；沉积  n. 存款；押金；沉积物",
            "derive", "v. 得到；源自",
            "deprive", "v. 剥夺；使丧失",
            "sediment", "n. 沉积物；沉淀物",
            "resource", "n. 资源；资料"
    );

    private WordRepository repository;
    private Path dataDirectory;
    private final ListView<Word> wordList = new ListView<>();
    private final Label pathLabel = new Label();
    private final Label countLabel = new Label();
    private final Label statusLabel = new Label("准备就绪");

    @Override
    public void start(Stage stage) {
        dataDirectory = Settings.loadDataDirectory().orElse(defaultDataDirectory());
        openRepository(dataDirectory);

        BorderPane root = new BorderPane();
        root.setTop(header(stage));
        root.setCenter(content());
        root.setBottom(statusBar());

        Scene scene = new Scene(root, 1120, 720);
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        stage.setTitle("English Word Bank · 单词星图 Demo");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
        refreshWords();
    }

    private HBox header(Stage stage) {
        Label title = new Label("English Word Bank");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("把陌生词变成属于你的认知网络");
        subtitle.getStyleClass().add("subtitle");
        VBox brand = new VBox(2, title, subtitle);

        Button chooseFolder = new Button("更改词库位置");
        chooseFolder.setOnAction(event -> chooseDataFolder(stage));
        pathLabel.getStyleClass().add("path-label");
        VBox location = new VBox(3, new Label("本地词库目录"), pathLabel);
        HBox box = new HBox(26, brand, new Region(), location, chooseFolder);
        HBox.setHgrow(box.getChildren().get(1), Priority.ALWAYS);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("header");
        updatePathLabel();
        return box;
    }

    private HBox content() {
        VBox addPanel = addPanel();
        VBox libraryPanel = libraryPanel();
        VBox insightPanel = insightPanel();
        HBox body = new HBox(18, addPanel, libraryPanel, insightPanel);
        HBox.setHgrow(libraryPanel, Priority.ALWAYS);
        body.setPadding(new Insets(20));
        return body;
    }

    private VBox addPanel() {
        Label heading = new Label("添加一个词");
        heading.getStyleClass().add("section-title");
        Label hint = new Label("先留下你的第一印象，再让它逐步长出更多意义。\n本 Demo 内置少量示例词典资料。 ");
        hint.setWrapText(true);
        hint.getStyleClass().add("muted");

        TextField wordInput = new TextField();
        wordInput.setPromptText("例如：deposit");
        TextArea impressionInput = new TextArea();
        impressionInput.setPromptText("我第一眼觉得它像什么？\n例如：和钱、银行有关");
        impressionInput.setPrefRowCount(4);
        TextArea definitionInput = new TextArea();
        definitionInput.setPromptText("基础释义（输入单词后会提供 Demo 建议）");
        definitionInput.setPrefRowCount(3);

        wordInput.textProperty().addListener((obs, oldValue, value) -> {
            String suggestion = DEMO_DICTIONARY.get(value.trim().toLowerCase());
            if (suggestion != null && definitionInput.getText().isBlank()) definitionInput.setText(suggestion);
        });

        Label thresholdLabel = new Label("形近敏感度：平衡（≥ 0.78）");
        Slider threshold = new Slider(0.60, 0.95, 0.78);
        threshold.setShowTickLabels(false);
        threshold.valueProperty().addListener((obs, old, value) -> thresholdLabel.setText(
                "形近敏感度：" + (value.doubleValue() < 0.72 ? "宽松" : value.doubleValue() < 0.86 ? "平衡" : "严格")
                        + "（≥ " + String.format("%.2f", value.doubleValue()) + "）"));

        Button add = new Button("加入我的词图");
        add.getStyleClass().add("primary-button");
        add.setMaxWidth(Double.MAX_VALUE);
        add.setOnAction(event -> addWord(wordInput, impressionInput, definitionInput, threshold.getValue()));

        VBox panel = new VBox(10,
                heading, hint,
                field("单词", wordInput),
                field("第一认知", impressionInput),
                field("词典资料", definitionInput),
                thresholdLabel, threshold, add);
        panel.getStyleClass().add("panel");
        panel.setPrefWidth(290);
        return panel;
    }

    private VBox libraryPanel() {
        Label heading = new Label("我的词库");
        heading.getStyleClass().add("section-title");
        countLabel.getStyleClass().add("badge");
        HBox top = new HBox(10, heading, countLabel);
        top.setAlignment(Pos.CENTER_LEFT);
        wordList.setPlaceholder(new Label("还没有单词。试着添加 deposit 和 derive。"));
        wordList.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(Word word, boolean empty) {
                super.updateItem(word, empty);
                if (empty || word == null) { setGraphic(null); return; }
                Label title = new Label(word.text()); title.getStyleClass().add("word-title");
                Label definition = new Label(word.definition()); definition.getStyleClass().add("definition");
                definition.setWrapText(true);
                Label impression = new Label("第一认知：" + blankAsDash(word.firstImpression()));
                impression.getStyleClass().add("impression");
                VBox card = new VBox(4, title, definition, impression);
                card.getStyleClass().add("word-card");
                setGraphic(card);
            }
        });
        VBox panel = new VBox(12, top, wordList);
        VBox.setVgrow(wordList, Priority.ALWAYS);
        panel.getStyleClass().add("panel");
        panel.setMinWidth(330);
        return panel;
    }

    private VBox insightPanel() {
        Label heading = new Label("本次发现的关联");
        heading.getStyleClass().add("section-title");
        Label body = new Label("添加单词后，这里会展示系统计算出的形近关系。\n\n例如：\nderive ↔ deprive\n共同轮廓：de + rive\n差异：deprive 在中间多了 p\n\n下一版可在这里加入 AI 语境释义、意象建议与词义网络。");
        body.setWrapText(true);
        body.getStyleClass().add("muted");
        VBox panel = new VBox(12, heading, body);
        panel.getStyleClass().addAll("panel", "insight-panel");
        panel.setPrefWidth(260);
        return panel;
    }

    private VBox field(String label, Control input) {
        Label name = new Label(label);
        return new VBox(4, name, input);
    }

    private HBox statusBar() {
        HBox box = new HBox(statusLabel);
        box.getStyleClass().add("status-bar");
        return box;
    }

    private void addWord(TextField wordInput, TextArea impressionInput, TextArea definitionInput, double threshold) {
        String text = wordInput.getText().trim().toLowerCase();
        if (!text.matches("[a-z][a-z' -]*")) {
            showError("请输入一个英文单词或短语。");
            return;
        }
        if (repository.find(text).isPresent()) {
            showError("“" + text + "”已在当前词库中。");
            return;
        }
        String definition = definitionInput.getText().trim();
        if (definition.isBlank()) definition = "暂无词典资料（Demo 可手动补充）";
        Word word = new Word(text, impressionInput.getText().trim(), definition);
        List<SimilarWord> matches = repository.addWordAndFindSimilar(word, threshold);
        refreshWords();
        wordInput.clear(); impressionInput.clear(); definitionInput.clear();
        statusLabel.setText(matches.isEmpty() ? "已加入 “" + text + "”，未发现超过阈值的形近词。"
                : "已加入 “" + text + "”，发现 " + matches.size() + " 个形近关联：" + matches.getFirst().word().text());
    }

    private void refreshWords() {
        List<Word> words = repository.allWords();
        wordList.setItems(FXCollections.observableArrayList(words));
        countLabel.setText(words.size() + " 个词");
    }

    private void chooseDataFolder(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择词库数据保存目录");
        chooser.setInitialDirectory(Files.isDirectory(dataDirectory) ? dataDirectory.toFile() : defaultDataDirectory().toFile());
        var selected = chooser.showDialog(stage);
        if (selected == null) return;
        Path next = selected.toPath().resolve("EnglishWordBank");
        openRepository(next);
        Settings.saveDataDirectory(next);
        updatePathLabel(); refreshWords();
        statusLabel.setText("已切换到词库目录：" + next);
    }

    private void openRepository(Path directory) {
        try { Files.createDirectories(directory); }
        catch (IOException e) { throw new IllegalStateException("无法创建词库目录：" + directory, e); }
        repository = new WordRepository(directory.resolve("word-bank.db"));
        dataDirectory = directory;
    }

    private Path defaultDataDirectory() {
        return Path.of(System.getProperty("user.home"), "Documents", "English Word Bank");
    }

    private void updatePathLabel() { pathLabel.setText(dataDirectory.toString()); }
    private static String blankAsDash(String value) { return value == null || value.isBlank() ? "—" : value; }
    private void showError(String message) { new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK).showAndWait(); }
}
