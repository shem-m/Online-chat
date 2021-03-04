package com.javarush.task.task30.task3008;

import org.w3c.dom.ls.LSOutput;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервер должен поддерживать множество соединений с разными клиентами одновременно.
 * Это можно реализовать с помощью следующего алгоритма:
 * <p>
 * - Сервер создает серверное сокетное соединение.
 * - В цикле ожидает, когда какой-то клиент подключится к сокету.
 * - Создает новый поток обработчик Handler, в котором будет происходить обмен сообщениями с клиентом.
 * - Ожидает следующее соединение.
 */


public class Server {
    private static Map<String, Connection> connectionMap = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        ConsoleHelper.writeMessage("Введите порт:");
        try (ServerSocket serverSocket = new ServerSocket(ConsoleHelper.readInt())) {
            ConsoleHelper.writeMessage("Сервер запущен");
            while (true) {
                Handler handler = new Handler(serverSocket.accept());
                handler.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendBroadcastMessage(Message message) {
        for (Map.Entry<String, Connection> entry : connectionMap.entrySet()) {
            try {
                entry.getValue().send(message);
            } catch (IOException e) {
                e.printStackTrace();
                ConsoleHelper.writeMessage("Ошибка отправки сообщения.");
            }
        }
    }

    /**
     * Класс Handler реализовывает протокол общения с клиентом.
     * Этап первый - это этап рукопожатия (знакомства сервера с клиентом).
     * Этап второй - отправка клиенту (новому участнику) информации об остальных клиентах (участниках) чата.
     * Этап третий - главный цикл обработки сообщений сервером.
     */

    private static class Handler extends Thread {
        private Socket socket;

        public Handler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            ConsoleHelper.writeMessage("Установлено соединение с удаленным адресом: " + socket.getRemoteSocketAddress());
            String userName = null;
            try (Connection connection = new Connection(socket)) {
                userName = serverHandshake(connection);
                sendBroadcastMessage(new Message(MessageType.USER_ADDED, userName));
                notifyUsers(connection, userName);
                serverMainLoop(connection, userName);

                connectionMap.remove(userName);
                sendBroadcastMessage(new Message(MessageType.USER_REMOVED, userName));
                ConsoleHelper.writeMessage("Соединение с удаленным адресом закрыто.");
                
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Произошла ошибка при обмене данными с удаленным адресом");
                e.printStackTrace();
            }

        }

        /**
         * Этап первый - это этап рукопожатия (знакомства сервера с клиентом).
         */
        private String serverHandshake(Connection connection) throws IOException, ClassNotFoundException {
            boolean flag = true;
            String name = null;
            while (flag) {
                connection.send(new Message(MessageType.NAME_REQUEST, "Введите имя."));
                Message receive = connection.receive();
                name = receive.getData();
                if (receive.getType() != MessageType.USER_NAME || name.isEmpty())
                    continue;
                else if (receive.getType() == MessageType.USER_NAME && name != null && name != "") {
                    if (!connectionMap.containsKey(name)) {
                        connectionMap.put(name, connection);
                        connection.send(new Message(MessageType.NAME_ACCEPTED, "Имя принято"));
                        flag = false;
                    }
                }
            }
            return name;
        }


        /**
         * Этап второй - отправка клиенту (новому участнику) информации об остальных клиентах (участниках) чата.
         */
        private void notifyUsers(Connection connection, String userName) {
            try {
                for (Map.Entry<String, Connection> entry : connectionMap.entrySet()) {
                    String name = entry.getKey();
                    if (name != userName) {
                        connection.send(new Message(MessageType.USER_ADDED, name));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Этап третий - главный цикл обработки сообщений сервером.
         */
        private void serverMainLoop(Connection connection, String userName) throws IOException, ClassNotFoundException {
            boolean flag = true;
            while (flag) {
                Message message = connection.receive();
                if (message.getType() == MessageType.TEXT) {
                    sendBroadcastMessage(new Message(MessageType.TEXT, userName + ": " + message.getData()));
                } else {
                    ConsoleHelper.writeMessage("Сообщение не было отправлено.");
                }
            }
        }
    }
}
