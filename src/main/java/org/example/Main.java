package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog; // 팝업창 클래스 추가
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.util.List;
import java.util.Optional; // Optional 클래스 추가

public class Main extends Application {

    private static final int TILE_SIZE = 70;
    private static final int WIDTH = 8;

    private GameModel gameModel;
    private NetworkClient networkClient;
    private AIPlayer aiPlayer;
    private int myColor = 0;

    private Stage primaryStage;
    private GridPane boardView;
    private Label scoreLabel;

    private Color getColorForPiece(int piece) {
        if (piece == 1) return Color.BLACK;
        if (piece == 2) return Color.WHITE;
        return Color.TRANSPARENT;
    }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        gameModel = new GameModel();
        aiPlayer = new AIPlayer(gameModel);

        showStartMenu();
    }

    // --- 1. 메인 메뉴 ---
    private void showStartMenu() {
        Label title = new Label("오셀로 게임 모드 선택");
        title.setStyle("-fx-font-size: 24px; -fx-padding: 20px;");

        Button btnLocal = createMenuButton("1:1 로컬 대전 (F-09)");
        Button btnOnline = createMenuButton("1:1 온라인 대전 (F-10)");
        Button btnAI = createMenuButton("AI와 대전 (Gemini)");

        btnLocal.setOnAction(e -> startGame(GameModel.Mode.LOCAL));
        // 온라인 버튼 클릭 시 IP 입력창 호출
        btnOnline.setOnAction(e -> startOnlineMatch());
        btnAI.setOnAction(e -> showAIDifficultyMenu());

        VBox menuBox = new VBox(15);
        menuBox.setAlignment(Pos.CENTER);
        menuBox.getChildren().addAll(title, btnLocal, btnOnline, btnAI);

        Scene menuScene = new Scene(menuBox, 400, 500);
        primaryStage.setScene(menuScene);
        primaryStage.setTitle("Othello Game - Menu");
        primaryStage.show();
    }

    private Button createMenuButton(String text) {
        Button btn = new Button(text);
        btn.setPrefWidth(200);
        btn.setPrefHeight(40);
        btn.setStyle("-fx-font-size: 14px;");
        return btn;
    }

    // --- 2. AI 난이도 선택 메뉴 ---
    private void showAIDifficultyMenu() {
        Label title = new Label("AI 난이도 선택");
        title.setStyle("-fx-font-size: 24px; -fx-padding: 20px;");

        Button btnEasy = createMenuButton("쉬움 (Easy)");
        Button btnMedium = createMenuButton("중간 (Medium)");
        Button btnHard = createMenuButton("어려움 (Hard)");
        Button btnBack = createMenuButton("뒤로가기");

        btnEasy.setOnAction(e -> {
            gameModel.setAIDifficulty(GameModel.Difficulty.EASY);
            startGame(GameModel.Mode.AI);
        });
        btnMedium.setOnAction(e -> {
            gameModel.setAIDifficulty(GameModel.Difficulty.MEDIUM);
            startGame(GameModel.Mode.AI);
        });
        btnHard.setOnAction(e -> {
            gameModel.setAIDifficulty(GameModel.Difficulty.HARD);
            startGame(GameModel.Mode.AI);
        });
        btnBack.setOnAction(e -> showStartMenu());

        VBox menuBox = new VBox(15);
        menuBox.setAlignment(Pos.CENTER);
        menuBox.getChildren().addAll(title, btnEasy, btnMedium, btnHard, btnBack);

        Scene scene = new Scene(menuBox, 400, 500);
        primaryStage.setScene(scene);
    }

    // --- 3. 게임 시작 및 화면 구성 ---
    private void startGame(GameModel.Mode mode) {
        gameModel.setGameMode(mode);
        gameModel.initializeBoard();

        boardView = createBoardView();
        scoreLabel = new Label("게임 준비 중...");
        scoreLabel.setStyle("-fx-font-size: 16px; -fx-padding: 10px;");

        Button backButton = new Button("메뉴로 나가기");
        backButton.setOnAction(e -> showStartMenu());

        BorderPane mainLayout = new BorderPane();
        HBox topBar = new HBox(backButton);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-padding: 10px; -fx-background-color: #eee;");

        HBox bottomBar = new HBox(scoreLabel);
        bottomBar.setAlignment(Pos.CENTER);
        bottomBar.setStyle("-fx-padding: 10px; -fx-background-color: #eee;");

        mainLayout.setTop(topBar);
        mainLayout.setCenter(boardView);
        mainLayout.setBottom(bottomBar);

        if (mode == GameModel.Mode.AI && gameModel.getCurrentTurn() == gameModel.getAIColor()) {
            Platform.runLater(this::handleAITurn);
        }

        drawBoard();
        drawValidMoves();
        updateScoreDisplay();

        Scene gameScene = new Scene(mainLayout, WIDTH * TILE_SIZE, WIDTH * TILE_SIZE + 100);
        primaryStage.setScene(gameScene);
        primaryStage.setTitle("Othello Game - Playing");
    }

    // --- 4. F-10 온라인 매칭 (IP 입력창 추가) ---
    public void startOnlineMatch() {
        // IP 입력 팝업 띄우기 (기본값: 내 컴퓨터)
        TextInputDialog dialog = new TextInputDialog("127.0.0.1");
        dialog.setTitle("서버 접속");
        dialog.setHeaderText("접속할 서버의 IP 주소를 입력하세요.");
        dialog.setContentText("IP 주소:");

        // 입력 대기
        Optional<String> result = dialog.showAndWait();

        // 확인 버튼을 눌렀을 때만 실행
        if (result.isPresent()) {
            String ipAddress = result.get();

            gameModel.setGameMode(GameModel.Mode.ONLINE);
            startGame(GameModel.Mode.ONLINE); // 게임 화면으로 먼저 전환

            if (networkClient != null && networkClient.isAlive()) return;

            // 입력받은 IP로 클라이언트 생성
            networkClient = new NetworkClient(this, ipAddress);

            if (networkClient.connect()) {
                networkClient.start();
                showAlert("매칭 대기", "서버(" + ipAddress + ")에 연결되었습니다.\n상대방을 기다리는 중...");
            } else {
                showAlert("연결 실패", "서버(" + ipAddress + ") 접속 실패.\nIP 주소와 NetworkServer 실행 여부를 확인하세요.");
                showStartMenu(); // 실패 시 메뉴로 복귀
            }
        }
    }

    // --- 5. 게임 로직 ---
    private GridPane createBoardView() {
        GridPane gridPane = new GridPane();
        gridPane.setStyle("-fx-background-color: #228B22;");
        for (int y = 0; y < WIDTH; y++) {
            for (int x = 0; x < WIDTH; x++) {
                StackPane stackPane = createTile(x, y);
                gridPane.add(stackPane, x, y);
                final int finalX = x;
                final int finalY = y;
                stackPane.setOnMouseClicked(e -> handleTileClick(finalX, finalY));
            }
        }
        return gridPane;
    }

    private void handleTileClick(int x, int y) {
        if (gameModel.isGameOver()) {
            showAlert("게임 종료", getWinnerMessage());
            return;
        }

        if (gameModel.isAIMode() && gameModel.getCurrentTurn() == gameModel.getAIColor()) return;
        if (gameModel.isOnlineMode()) {
            if (myColor == 0 || gameModel.getCurrentTurn() != myColor) return;
        }

        boolean flipped = gameModel.placePieceAndFlip(x, y);

        if (flipped) {
            if (gameModel.isOnlineMode()) {
                networkClient.sendMove(x, y);
            }
            updateGameViewAfterMove();

            if (gameModel.isAIMode() && !gameModel.isGameOver()) {
                Platform.runLater(this::handleAITurn);
            }
        } else {
            showAlert("잘못된 수", "둘 수 없는 위치입니다.");
        }
    }

    private void handleAITurn() {
        if (gameModel.getCurrentTurn() != gameModel.getAIColor()) return;

        new Thread(() -> {
            try {
                Thread.sleep(700);
                int[] move = aiPlayer.getBestMove(gameModel.getAIDifficulty());
                Platform.runLater(() -> {
                    if (move != null) {
                        gameModel.placePieceAndFlip(move[0], move[1]);
                        updateGameViewAfterMove();
                    } else {
                        gameModel.switchTurn();
                        checkPassConditions();
                        updateGameViewAfterMove();
                        showAlert("AI 패스", "AI가 둘 곳이 없어 패스합니다.");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("AI 오류", "오류 발생: " + e.getMessage()));
            }
        }).start();
    }

    private void updateGameViewAfterMove() {
        gameModel.switchTurn();
        checkPassConditions();
        drawBoard();
        drawValidMoves();
        updateScoreDisplay();
    }

    public void processOpponentMove(int x, int y) {
        Platform.runLater(() -> {
            boolean flipped = gameModel.placePieceAndFlip(x, y);
            if (flipped) {
                updateGameViewAfterMove();
                showAlert("당신의 턴", "상대방이 수를 두었습니다.");
            }
        });
    }

    public void setPlayerColor(String color) {
        Platform.runLater(() -> {
            gameModel.initializeBoard();
            if (color.equals("BLACK")) {
                myColor = 1;
                showAlert("매칭 성공", "당신은 흑돌(⚫)입니다. 선공하세요!");
            } else {
                myColor = 2;
                showAlert("매칭 성공", "당신은 백돌(⚪)입니다. 상대방을 기다리세요.");
            }
            drawBoard();
            drawValidMoves();
            updateScoreDisplay();
        });
    }

    private void checkPassConditions() {
        if (gameModel.getValidMoves().isEmpty()) {
            gameModel.switchTurn();
            if (gameModel.getValidMoves().isEmpty()) {
                gameModel.setGameOver(true);
                showAlert("게임 종료", getWinnerMessage());
            } else {
                showAlert("패스", "둘 곳이 없어 턴을 넘깁니다.");
            }
        }
    }

    private String getWinnerMessage() {
        int black = gameModel.getScore(1);
        int white = gameModel.getScore(2);
        if (black > white) return "흑돌 승리! (" + black + ":" + white + ")";
        if (white > black) return "백돌 승리! (" + white + ":" + black + ")";
        return "무승부!";
    }

    private void drawValidMoves() {
        for (int y = 0; y < WIDTH; y++) {
            for (int x = 0; x < WIDTH; x++) {
                StackPane stackPane = (StackPane) boardView.getChildren().get(y * WIDTH + x);
                stackPane.getChildren().removeIf(node -> node instanceof Circle && node.getStyleClass().contains("valid"));
            }
        }
        if (!gameModel.isGameOver()) {
            if (gameModel.isOnlineMode() && gameModel.getCurrentTurn() != myColor) return;
            if (gameModel.isAIMode() && gameModel.getCurrentTurn() == gameModel.getAIColor()) return;

            List<int[]> moves = gameModel.getValidMoves();
            for (int[] m : moves) {
                StackPane sp = (StackPane) boardView.getChildren().get(m[1] * WIDTH + m[0]);
                Circle hint = new Circle(10, Color.rgb(0, 0, 0, 0.2));
                hint.getStyleClass().add("valid");
                sp.getChildren().add(hint);
            }
        }
    }

    private void updateScoreDisplay() {
        int b = gameModel.getScore(1);
        int w = gameModel.getScore(2);
        String turn = (gameModel.getCurrentTurn() == 1) ? "Black" : "White";
        scoreLabel.setText(String.format("Black: %d  |  White: %d  |  Turn: %s", b, w, turn));
    }

    private void drawBoard() {
        for (int y = 0; y < WIDTH; y++) {
            for (int x = 0; x < WIDTH; x++) {
                StackPane stackPane = (StackPane) boardView.getChildren().get(y * WIDTH + x);
                stackPane.getChildren().removeIf(node -> node instanceof Circle);
                int piece = gameModel.getBoard()[y][x];
                if (piece != 0) {
                    stackPane.getChildren().add(createPiece(getColorForPiece(piece)));
                }
            }
        }
    }

    private StackPane createTile(int x, int y) {
        Rectangle tile = new Rectangle(TILE_SIZE, TILE_SIZE);
        tile.setFill((x + y) % 2 == 0 ? Color.web("#228B22") : Color.web("#006400"));
        tile.setStroke(Color.BLACK);
        return new StackPane(tile);
    }

    private Circle createPiece(Color color) {
        Circle piece = new Circle(TILE_SIZE * 0.4, color);
        piece.setStroke(Color.BLACK);
        piece.setStrokeWidth(2);
        return piece;
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch();
    }
}