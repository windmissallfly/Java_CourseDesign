import java.io.*;
import java.net.*;

public class ChatClient {
    public static void main(String[] args) throws IOException {
        Socket socket = new Socket("39.107.61.84", 9999);
        ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
        ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("请输入用户名: ");
        String clientId = reader.readLine();
        oos.writeUTF(clientId);

        new Thread(() -> {
            try {
                String message;
                while ((message = ois.readUTF()) != null) {
                    System.out.println(message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        String message;
        while (!(message = reader.readLine()).equalsIgnoreCase("exit")) {
            oos.writeUTF(message);
        }

        socket.close();
    }
}