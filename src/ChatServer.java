import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private static ConcurrentHashMap<String, Socket> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(9999);
        System.out.println("服务器启动，等待客户端连接...");

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(new ClientHandler(socket)).start();
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private String clientId;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                 ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {

                clientId = ois.readUTF();
                clients.put(clientId, socket);
                System.out.println(clientId + " 已连接");

                String message;
                while ((message = ois.readUTF()) != null) {
                    System.out.println(clientId + ": " + message);
                    for (Socket clientSocket : clients.values()) {
                        if (clientSocket != socket) {
                            new ObjectOutputStream(clientSocket.getOutputStream()).writeUTF(clientId + ": " + message);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                clients.remove(clientId);
                System.out.println(clientId + " 已断开连接");
            }
        }
    }
}
