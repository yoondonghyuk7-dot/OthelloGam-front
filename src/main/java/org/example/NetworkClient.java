package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class NetworkClient extends Thread {

    // 상수가 아닌 변수로 변경 (생성자에서 설정됨)
    private String serverIp;
    private static final int SERVER_PORT = 8080;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Main gameController;

    // 생성자 수정: IP 주소를 인자로 받음
    public NetworkClient(Main gameController, String serverIp) {
        this.gameController = gameController;
        this.serverIp = serverIp;
    }

    public boolean connect() {
        try {
            // 전달받은 IP로 접속 시도
            socket = new Socket(serverIp, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println("Connected to server (" + serverIp + "). Waiting for opponent...");
            return true;
        } catch (IOException e) {
            System.err.println("Failed to connect to server: " + e.getMessage());
            return false;
        }
    }

    public void sendMove(int x, int y) {
        if (out != null) {
            out.println("MOVE " + x + " " + y);
        }
    }

    @Override
    public void run() {
        try {
            String serverResponse;
            while ((serverResponse = in.readLine()) != null) {
                if (serverResponse.startsWith("START_")) {
                    String color = serverResponse.substring(6);
                    gameController.setPlayerColor(color);
                } else if (serverResponse.startsWith("MOVE")) {
                    String[] parts = serverResponse.split(" ");
                    if (parts.length == 3) {
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        gameController.processOpponentMove(x, y);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Connection lost to server.");
        } finally {
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {}
        }
    }
}