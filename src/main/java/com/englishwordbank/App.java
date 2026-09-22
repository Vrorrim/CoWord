package com.englishwordbank;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;

public class App extends Application {
    private final DictionaryService dictionaryService = new DictionaryService();
    private final EcdictInstaller ecdictInstaller = new EcdictInstaller();
    private WordRepository repository;
    private Path dataDirectory;
    private final ListView<Word> wordList = new ListView<>();
    private final ObservableList<Word> libraryWords = FXCollections.observableArrayList();
    private final Label pathLabel = new Label();
    private final Label countLabel = new Label();
    private final Label statusLabel = new Label("准备就绪");
    private String searchQuery = "";

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
        brand.setMinWidth(200);
        brand.setPrefWidth(250);
        brand.setMaxWidth(250);

        Button chooseFolder = new Button("更改词库位置");
        chooseFolder.setOnAction(event -> chooseDataFolder(stage));
        Button studyCards = new Button("开始词卡学习");
        studyCards.getStyleClass().add("study-button");
        studyCards.setOnAction(event -> openStudyCards());
        pathLabel.getStyleClass().add("path-label");
        TextField search = new TextField();
        search.setPromptText("搜索单词或中文释义");
        search.getStyleClass().add("header-search");
        search.textProperty().addListener((obs, oldValue, value) -> { searchQuery = value.trim().toLowerCase(); refreshWords(); });
        Button addWord = new Button("+");
        addWord.getStyleClass().add("header-add-button");
        addWord.setTooltip(new Tooltip("添加单词"));
        addWord.setOnAction(event -> openAddWordWindow());
        HBox searchBox = new HBox(0, search, addWord);
        searchBox.getStyleClass().add("header-search-box");
        searchBox.setMinWidth(220);
        searchBox.setPrefWidth(310);
        searchBox.setMaxWidth(310);
        VBox location = new VBox(3, new Label("本地词库目录"), pathLabel);
        location.setMinWidth(170);
        location.setPrefWidth(250);
        location.setMaxWidth(250);
        pathLabel.setMaxWidth(250);
        pathLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        HBox box = new HBox(14, brand, new Region(), searchBox, location, studyCards, chooseFolder);
        HBox.setHgrow(box.getChildren().get(1), Priority.ALWAYS);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("header");
        updatePathLabel();
        return box;
    }

    private VBox content() {
        VBox library = libraryPanel();
        library.setMaxWidth(Double.MAX_VALUE);
        library.setPrefWidth(Region.USE_COMPUTED_SIZE);
        VBox body = new VBox(library);
        VBox.setVgrow(library, Priority.ALWAYS);
        body.setPadding(new Insets(20, 70, 20, 70));
        return body;
    }

    private VBox addPanel() {
        Label heading = new Label("添加一个词");
        heading.getStyleClass().add("section-title");
        Label hint = new Label("先查询词典资料，再留下你的第一认知。词典查询不会阻塞界面。");
        hint.setWrapText(true);
        hint.getStyleClass().add("muted");

        TextField wordInput = new TextField();
        wordInput.setPromptText("例如：deposit");
        TextArea impressionInput = new TextArea();
        impressionInput.setPromptText("我第一眼觉得它像什么？\n例如：和钱、银行有关");
        impressionInput.setPrefRowCount(4);
        TextArea definitionInput = new TextArea();
        definitionInput.setPromptText("点击“查询词典”后自动填充；你也可以手动编辑");
        definitionInput.setPrefRowCount(3);
        Label dictionaryStatus = new Label("尚未查询");
        dictionaryStatus.getStyleClass().add("muted");
        Button lookup = new Button("查询词典");
        lookup.setOnAction(event -> lookupDictionary(wordInput, definitionInput, dictionaryStatus, lookup));
        Label chineseDictionaryStatus = new Label(repository.hasChineseDictionary()
                ? "英汉词典已安装：离线优先"
                : "请先安装离线英汉词典，以获得中文释义");
        chineseDictionaryStatus.getStyleClass().add("muted");
        Button installChineseDictionary = new Button("安装离线英汉词典（约 63MB）");
        installChineseDictionary.setOnAction(event -> installChineseDictionary(installChineseDictionary, chineseDictionaryStatus));

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
                installChineseDictionary, chineseDictionaryStatus,
                lookup, dictionaryStatus,
                thresholdLabel, threshold, add);
        panel.getStyleClass().add("panel");
        panel.setPrefWidth(460);
        return panel;
    }

    private VBox libraryPanel() {
        Label heading = new Label("我的词库");
        heading.getStyleClass().add("section-title");
        countLabel.getStyleClass().add("badge");
        Button manageLibrary = new Button("词库管理");
        manageLibrary.setOnAction(event -> openLibraryManager());
        HBox top = new HBox(10, heading, countLabel, new Region(), manageLibrary);
        HBox.setHgrow(top.getChildren().get(2), Priority.ALWAYS);
        top.setAlignment(Pos.CENTER_LEFT);
        wordList.setPlaceholder(new Label("还没有单词。试着添加 deposit 和 derive。"));
        wordList.setItems(libraryWords);
        wordList.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(Word word, boolean empty) {
                super.updateItem(word, empty);
                if (empty || word == null) { setGraphic(null); return; }
                Label title = new Label(word.text()); title.getStyleClass().add("word-title");
                Label definition = new Label(coreChineseMeaning(word.definition())); definition.getStyleClass().add("compact-definition");
                definition.setWrapText(false);
                Label mastery = new Label("学习状态：" + word.mastery().label());
                mastery.getStyleClass().add("mastery-label");
                Region spacer = new Region();
                HBox row = new HBox(14, title, definition, spacer, mastery);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().addAll("word-card", "compact-word-row");
                setGraphic(row);
            }
            {
                setOnDragDetected(event -> {
                    if (getItem() == null || !searchQuery.isBlank()) return;
                    Dragboard dragboard = startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(getItem().text());
                    dragboard.setContent(content);
                    event.consume();
                });
                setOnDragOver(event -> {
                    if (event.getGestureSource() != this && event.getDragboard().hasString()) event.acceptTransferModes(TransferMode.MOVE);
                    event.consume();
                });
                setOnDragEntered(event -> { if (event.getGestureSource() != this) getStyleClass().add("drag-target"); });
                setOnDragExited(event -> getStyleClass().remove("drag-target"));
                setOnDragDropped(event -> {
                    boolean moved = false;
                    String sourceText = event.getDragboard().getString();
                    if (sourceText != null && getItem() != null) {
                        int from = indexOf(sourceText);
                        int target = getIndex();
                        if (from >= 0 && from != target) {
                            Word source = libraryWords.remove(from);
                            if (from < target) target--;
                            libraryWords.add(target, source);
                            repository.reorderWords(List.copyOf(libraryWords));
                            statusLabel.setText("已调整 “" + source.text() + "” 在词库中的位置。");
                            moved = true;
                        }
                    }
                    event.setDropCompleted(moved);
                    event.consume();
                });
            }
        });
        Label reorderHint = new Label("输入关键词可搜索；上下拖动词条可调整顺序；删除请使用“词库管理”。");
        reorderHint.getStyleClass().add("muted");
        VBox panel = new VBox(8, top, reorderHint, wordList);
        VBox.setVgrow(wordList, Priority.ALWAYS);
        panel.getStyleClass().add("panel");
        panel.setMinWidth(600);
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

    private void lookupDictionary(TextField wordInput, TextArea definitionInput, Label dictionaryStatus, Button lookupButton) {
        String text = wordInput.getText().trim().toLowerCase();
        if (!text.matches("[a-z][a-z' -]*")) {
            showError("请输入一个英文单词或短语后再查询。");
            return;
        }
        if (repository.findChineseDictionaryEntry(text).map(entry -> {
            String detail = "中文释义\n" + entry.translation()
                    + (entry.definition().isBlank() ? "" : "\n\n英文补充释义\n" + entry.definition());
            if (!entry.phonetic().isBlank()) detail = "/" + entry.phonetic() + "/\n" + detail;
            definitionInput.setText(detail);
            dictionaryStatus.setText("来源：ECDICT（MIT）· 本地离线英汉词典");
            statusLabel.setText("已获取 “" + entry.word() + "” 的中文释义，请补充第一认知后加入词图。");
            return true;
        }).orElse(false)) {
            return;
        }
        if (!repository.hasChineseDictionary()) {
            dictionaryStatus.setText("请先点击“安装离线英汉词典”，安装完成后即可查询中文释义。");
            statusLabel.setText("尚未安装离线英汉词典。");
            return;
        }
        lookupButton.setDisable(true);
        dictionaryStatus.setText("正在查询词典…");
        dictionaryService.lookup(text).whenComplete((result, error) -> Platform.runLater(() -> {
            lookupButton.setDisable(false);
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                String message = cause instanceof DictionaryService.DictionaryException
                        ? cause.getMessage() : "网络连接失败，请检查网络后重试。";
                dictionaryStatus.setText("查询失败：" + message);
                statusLabel.setText("词典查询失败，未写入词库。");
                return;
            }
            String detail = result.phonetic().isBlank() ? result.definition() : result.phonetic() + "\n" + result.definition();
            definitionInput.setText(detail);
            dictionaryStatus.setText("来源：" + result.source() + " · 已提取常用义项");
            statusLabel.setText("已从词典获取 “" + result.word() + "” 的资料，请补充第一认知后加入词图。");
        }));
    }

    private void installChineseDictionary(Button installButton, Label installationStatus) {
        if (repository.hasChineseDictionary()) {
            installationStatus.setText("英汉词典已安装；重新安装会覆盖本地词典数据。");
        }
        installButton.setDisable(true);
        installationStatus.setText("正在下载并导入英汉词典，这可能需要几分钟…");
        statusLabel.setText("正在安装离线英汉词典，请保持应用开启。");
        ecdictInstaller.install(dataDirectory, repository).whenComplete((count, error) -> Platform.runLater(() -> {
            installButton.setDisable(false);
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                installationStatus.setText("安装失败：" + cause.getMessage());
                statusLabel.setText("离线英汉词典安装失败。");
                return;
            }
            installationStatus.setText("英汉词典已安装：" + count + " 条中文词条，可离线查询。");
            statusLabel.setText("离线英汉词典安装完成，中文释义已可用。");
        }));
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
        Word word = new Word(text, impressionInput.getText().trim(), definition, Word.Mastery.NEW);
        List<SimilarWord> matches = repository.addWordAndFindSimilar(word, threshold);
        refreshWords();
        wordInput.clear(); impressionInput.clear(); definitionInput.clear();
        statusLabel.setText(matches.isEmpty() ? "已加入 “" + text + "”，未发现超过阈值的形近词。"
                : "已加入 “" + text + "”，发现 " + matches.size() + " 个形近关联：" + matches.getFirst().word().text());
    }

    private void refreshWords() {
        List<Word> allWords = repository.allWords();
        List<Word> words = searchQuery.isBlank() ? allWords : allWords.stream()
                .filter(word -> word.text().contains(searchQuery) || word.definition().toLowerCase().contains(searchQuery))
                .toList();
        libraryWords.setAll(words);
        countLabel.setText(searchQuery.isBlank() ? allWords.size() + " 个词" : words.size() + " / " + allWords.size() + " 个词");
    }

    private int indexOf(String word) {
        for (int i = 0; i < libraryWords.size(); i++) if (libraryWords.get(i).text().equals(word)) return i;
        return -1;
    }

    private void deleteWord(Word word) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "确定删除 “" + word.text() + "” 吗？它的个人笔记和形近关联也会一并删除。",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.setTitle("删除单词");
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        repository.deleteWord(word.text());
        refreshWords();
        statusLabel.setText("已删除 “" + word.text() + "”。");
    }

    private void openStudyCards() {
        List<Word> words = repository.allWords();
        if (words.isEmpty()) {
            showError("词库还是空的，先添加几个单词再开始词卡学习。");
            return;
        }
        new WordCardWindow(repository, words, this::refreshWords).show();
    }

    private void openLibraryManager() {
        new LibraryManagerWindow(repository, this::refreshWords).show();
    }

    private void openAddWordWindow() {
        Stage stage = new Stage();
        stage.setTitle("English Word Bank · 添加单词");
        VBox root = new VBox(addPanel());
        root.setPadding(new Insets(20));
        root.getStyleClass().add("add-window-root");
        Scene scene = new Scene(root, 510, 660);
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(460);
        stage.show();
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
    private static String coreChineseMeaning(String definition) {
        String normalized = definition.replace("\r", "");
        int chinese = normalized.indexOf("中文释义\n");
        if (chinese >= 0) normalized = normalized.substring(chinese + "中文释义\n".length());
        int english = normalized.indexOf("\n\n英文补充释义");
        if (english >= 0) normalized = normalized.substring(0, english);
        String firstLine = normalized.lines().filter(line -> !line.isBlank()).findFirst().orElse("暂无中文释义");
        return firstLine.length() > 42 ? firstLine.substring(0, 42) + "…" : firstLine;
    }
    private static String blankAsDash(String value) { return value == null || value.isBlank() ? "—" : value; }
    private void showError(String message) { new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK).showAndWait(); }
}
