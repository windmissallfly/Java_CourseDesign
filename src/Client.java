import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Client extends JFrame {
    // GUI组件
    private JTextField serverField, portField, nameField;
    private JTextArea chatArea, inputArea;
    private JButton connectButton, sendButton, exitButton;

    // 网络相关
    private Socket socket;
    private PrintWriter out;
    private boolean isConnected = false;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public Client() {
        initUI();
        setVisible(true);
    }

    private void initUI() {
        setTitle("聊天室客户端");
        setSize(800, 500);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                disconnect();
                dispose();
            }
        });

        // 连接面板
        JPanel connectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        serverField = new JTextField("localhost", 10);
        portField = new JTextField("3083", 5);
        nameField = new JTextField(10);
        connectButton = new JButton("进入聊天室");
        exitButton = new JButton("退出聊天室");

        connectPanel.add(new JLabel("服务器:"));
        connectPanel.add(serverField);
        connectPanel.add(new JLabel("端口:"));
        connectPanel.add(portField);
        connectPanel.add(new JLabel("昵称:"));
        connectPanel.add(nameField);
        connectPanel.add(connectButton);
        connectPanel.add(exitButton);

        // 聊天区域
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(new TitledBorder("聊天内容"));

        // 输入区域
        inputArea = new JTextArea(3, 20);
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(new TitledBorder("输入消息"));

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        sendButton = new JButton("发送");
        buttonPanel.add(sendButton);

        // 布局
        setLayout(new BorderLayout());
        add(connectPanel, BorderLayout.NORTH);
        add(chatScroll, BorderLayout.CENTER);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(inputScroll, BorderLayout.CENTER);
        southPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(southPanel, BorderLayout.SOUTH);

        // 事件监听
        connectButton.addActionListener(e -> connect());
        exitButton.addActionListener(e -> disconnect());
        sendButton.addActionListener(e -> sendMessage());
        inputArea.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    sendMessage();
                }
            }
        });
        updateUIState();
    }

    private void connect() {
        String host = serverField.getText().trim();
        String portStr = portField.getText().trim();
        String name = nameField.getText().trim();

        if (host.isEmpty() || portStr.isEmpty() || name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请填写所有连接信息");
            return;
        }

        try {
            int port = Integer.parseInt(portStr);
            if (port < 1024 || port > 65535) {
                JOptionPane.showMessageDialog(this, "端口号无效");
                return;
            }

            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println(name);
            String response = in.readLine();
            if (!"OK".equals(response)) {
                JOptionPane.showMessageDialog(this, "昵称已被占用或无效");
                socket.close();
                return;
            }

            isConnected = true;
            updateUIState();
            chatArea.append("[系统] 已连接到服务器\n");

            new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        processMessage(line);
                    }
                } catch (IOException e) {
                    if (isConnected) disconnect();
                }
            }).start();

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "端口号必须为整数");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "连接服务器失败");
        }
    }

    private void disconnect() {
        if (isConnected) {
            out.println("exit");
            try { socket.close(); } catch (IOException e) { }
            isConnected = false;
            updateUIState();
            chatArea.append("[系统] 已断开连接\n");
        }
    }

    private void sendMessage() {
        if (!isConnected) return;
        String message = inputArea.getText().trim().replace("\n", " ");
        if (!message.isEmpty()) {
            String time = timeFormat.format(new Date());
            out.println("MSG:[" + time + "] " + message);
            inputArea.setText("");
        }
    }

    private void processMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            if (message.startsWith("SYS:")) {
                handleSystemMessage(message.substring(4));
            } else if (message.startsWith("MSG:")) {
                chatArea.append(message.substring(4) + "\n");
            }
        });
    }

    private void handleSystemMessage(String message) {
        if (message.equals("server_shutdown")) {
            chatArea.append("[系统] 服务器已关闭\n");
            disconnect();
        } else if (message.equals("kick")) {
            chatArea.append("[系统] 你已被管理员踢出\n");
            disconnect();
        } else {
            chatArea.append("[系统] " + message + "\n");
        }
    }

    private void updateUIState() {
        boolean connected = isConnected;
        connectButton.setEnabled(!connected);
        exitButton.setEnabled(connected);
        serverField.setEnabled(!connected);
        portField.setEnabled(!connected);
        nameField.setEnabled(!connected);
        sendButton.setEnabled(connected);
        inputArea.setEnabled(connected);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Client::new);
    }
}