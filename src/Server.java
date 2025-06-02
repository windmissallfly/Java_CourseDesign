import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class Server extends JFrame {
    // GUI组件
    private JTextArea logArea;
    private JTextField portField, kickField, msgField;
    private JButton startButton, stopButton, kickButton, sendButton;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;

    // 网络相关
    private ServerSocket serverSocket;
    private static final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private boolean isRunning = false;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public Server() {
        initUI();
        setVisible(true);
    }

    private void initUI() {
        setTitle("聊天室服务器");
        setSize(1000, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // 顶部面板
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        portField = new JTextField("3083", 6);
        kickField = new JTextField(10);
        msgField = new JTextField(25);
        startButton = new JButton("启动服务");
        stopButton = new JButton("停止服务");
        kickButton = new JButton("踢出用户");
        sendButton = new JButton("发送消息");

        topPanel.add(new JLabel("端口:"));
        topPanel.add(portField);
        topPanel.add(startButton);
        topPanel.add(stopButton);
        topPanel.add(new JLabel("踢出昵称:"));
        topPanel.add(kickField);
        topPanel.add(kickButton);
        topPanel.add(new JLabel("消息:"));
        topPanel.add(msgField);
        topPanel.add(sendButton);

        // 用户列表
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        JScrollPane listScroll = new JScrollPane(userList);
        listScroll.setBorder(new TitledBorder("在线用户"));
        listScroll.setPreferredSize(new Dimension(150, 0));

        // 日志区域
        logArea = new JTextArea();
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder("聊天记录"));

        // 布局
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, logScroll);
        splitPane.setDividerLocation(160);

        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        // 事件监听
        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());
        kickButton.addActionListener(e -> kickUser());
        sendButton.addActionListener(e -> sendServerMessage());
        stopButton.setEnabled(false);
        msgField.addActionListener(e -> sendServerMessage());
    }

    private void startServer() {
        try {
            int port = validatePort(portField.getText());
            log("正在启动服务...");
            isRunning = true;
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            portField.setEnabled(false);
            userListModel.clear();

            serverSocket = new ServerSocket(port);

            // 迎宾线程
            new Thread(() -> {
                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        ClientHandler handler = new ClientHandler(clientSocket);
                        clients.add(handler);
                        new Thread(handler).start();
                    } catch (IOException e) { /* 处理关闭 */ }
                }
            }).start();

            // 巡检线程
            new Thread(new Inspector()).start();

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "端口号必须是1024-65535之间的整数");
        } catch (IOException e) {
            log("启动服务失败: " + e.getMessage());
        }
    }

    private void stopServer() {
        isRunning = false;
        broadcast("SYS:server_shutdown");
        try {
            serverSocket.close();
        } catch (IOException e) {
            log("停止服务时发生错误: " + e.getMessage());
        }
        clients.clear();
        userListModel.clear();
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        portField.setEnabled(true);
        log("服务已停止");
    }

    private void kickUser() {
        String nickname = kickField.getText().trim();
        if (nickname.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入要踢出的昵称");
            return;
        }

        for (ClientHandler client : clients) {
            if (client.nickname.equals(nickname)) {
                client.sendMessage("SYS:kick");
                clients.remove(client);
                log("已踢出用户: " + nickname);
                broadcast("SYS:" + nickname + " 已被管理员踢出");
                return;
            }
        }
        JOptionPane.showMessageDialog(this, "用户不存在");
    }

    private void sendServerMessage() {
        String message = msgField.getText().trim();
        if (!message.isEmpty()) {
            String time = timeFormat.format(new Date());
            String fullMsg = "MSG:管理员 [" + time + "]: " + message;
            broadcast(fullMsg);
            logArea.append(fullMsg.substring(4) + "\n");
            msgField.setText("");
        }
    }

    private int validatePort(String portStr) throws NumberFormatException {
        int port = Integer.parseInt(portStr);
        if (port < 1024 || port > 65535)
            throw new NumberFormatException();
        return port;
    }

    public static void broadcast(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() ->
                logArea.append("[系统] " + message + "\n"));
    }

    class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String nickname;

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // 验证昵称
            nickname = in.readLine();
            if (!validateNickname(nickname)) {
                out.println("ERROR");
                throw new IOException("无效昵称: " + nickname);
            }
            out.println("OK");
            broadcast("SYS:" + nickname + " 加入了聊天室");
            SwingUtilities.invokeLater(() -> userListModel.addElement(nickname));
            log(nickname + " 已连接 (" + socket.getInetAddress() + ")");
        }

        private boolean validateNickname(String name) {
            if (name.equalsIgnoreCase("管理员")) return false;
            return clients.stream().noneMatch(c -> c.nickname.equals(name));
        }

        @Override
        public void run() {
            try {
                String input;
                while ((input = in.readLine()) != null) {
                    if (input.equalsIgnoreCase("exit")) break;
                    if (input.startsWith("MSG:")) {
                        String content = input.substring(4);
                        String time = timeFormat.format(new Date());
                        String broadcastMsg = "MSG:" + nickname + " [" + time + "]: " + content;
                        broadcast(broadcastMsg);
                        SwingUtilities.invokeLater(() ->
                                logArea.append(broadcastMsg.substring(4) + "\n"));
                    }
                }
            } catch (IOException e) {
                log(nickname + " 异常断开");
            } finally {
                clients.remove(this);
                broadcast("SYS:" + nickname + " 离开了聊天室");
                SwingUtilities.invokeLater(() -> userListModel.removeElement(nickname));
                try { socket.close(); } catch (IOException e) { }
                log(nickname + " 连接已关闭");
            }
        }

        public void sendMessage(String msg) {
            out.println(msg);
        }
    }

    class Inspector implements Runnable {
        @Override
        public void run() {
            while (isRunning) {
                try {
                    Thread.sleep(30000);
                    clients.removeIf(client -> {
                        try {
                            client.sendMessage("PING");
                            return false;
                        } catch (Exception e) {
                            SwingUtilities.invokeLater(() ->
                                    userListModel.removeElement(client.nickname));
                            return true;
                        }
                    });
                } catch (InterruptedException e) { }
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Server::new);
    }
}