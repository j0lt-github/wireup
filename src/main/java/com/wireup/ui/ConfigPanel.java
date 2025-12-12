package com.wireup.ui;

import com.wireup.utils.Logger;
import com.wireup.vpn.ConnectionManager;
import com.wireup.vpn.WireGuardConfig;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;

/**
 * Panel for WireGuard configuration management
 */
public class ConfigPanel {

    private final Logger logger;

    private JPanel panel;
    private JTextArea configTextArea;
    private JLabel validationLabel;
    private JButton loadFileButton;
    private JButton validateButton;

    // Protocol selection
    private JRadioButton wireGuardButton;
    private JRadioButton openVpnButton;
    private ButtonGroup protocolGroup;

    // OpenVPN authentication fields
    private JPanel authPanel;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JTextField totpField;
    private JLabel authStatusLabel;

    public ConfigPanel(ConnectionManager connectionManager, Logger logger) {
        this.logger = logger;

        initializeUI();
    }

    private void initializeUI() {
        panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "VPN Configuration",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 12)));

        // Protocol Selection Panel
        JPanel protocolPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        protocolPanel.add(new JLabel("Protocol:"));

        wireGuardButton = new JRadioButton("WireGuard", true);
        openVpnButton = new JRadioButton("OpenVPN");

        protocolGroup = new ButtonGroup();
        protocolGroup.add(wireGuardButton);
        protocolGroup.add(openVpnButton);

        protocolPanel.add(wireGuardButton);
        protocolPanel.add(openVpnButton);

        // Add listener to re-validate on switch and toggle auth panel
        java.awt.event.ActionListener protocolListener = e -> {
            validateConfig();
            // Show auth panel only for OpenVPN
            if (authPanel != null) {
                authPanel.setVisible(openVpnButton.isSelected());
            }
        };
        wireGuardButton.addActionListener(protocolListener);
        openVpnButton.addActionListener(protocolListener);

        // Config text area
        configTextArea = new JTextArea(15, 50);
        configTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        configTextArea.setLineWrap(false);
        configTextArea.setWrapStyleWord(false);
        configTextArea.setText("# Paste your WireGuard configuration here\n" +
                "# Or use 'Load from File' button below\n\n" +
                "[Interface]\n" +
                "PrivateKey = YOUR_PRIVATE_KEY\n" +
                "Address = 10.0.0.2/24\n" +
                "DNS = 1.1.1.1\n\n" +
                "[Peer]\n" +
                "PublicKey = PEER_PUBLIC_KEY\n" +
                "Endpoint = vpn.example.com:51820\n" +
                "AllowedIPs = 0.0.0.0/0\n" +
                "PersistentKeepalive = 25");

        JScrollPane scrollPane = new JScrollPane(configTextArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        // Authentication Panel (for OpenVPN)
        authPanel = createAuthPanel();
        authPanel.setVisible(false); // Hidden by default

        // Main content wrapper
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.add(protocolPanel, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        centerPanel.add(authPanel, BorderLayout.SOUTH);

        panel.add(centerPanel, BorderLayout.CENTER);

        // Bottom panel with buttons and validation
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        loadFileButton = new JButton("Load from File");
        loadFileButton.addActionListener(e -> loadConfigFromFile());

        validateButton = new JButton("Validate Config");
        validateButton.addActionListener(e -> validateConfig());

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> configTextArea.setText(""));

        buttonPanel.add(loadFileButton);
        buttonPanel.add(validateButton);
        buttonPanel.add(clearButton);

        // Validation label
        validationLabel = new JLabel(" ");
        validationLabel.setFont(new Font("Arial", Font.PLAIN, 11));

        bottomPanel.add(buttonPanel, BorderLayout.WEST);
        bottomPanel.add(validationLabel, BorderLayout.CENTER);

        panel.add(bottomPanel, BorderLayout.SOUTH);
    }

    private void loadConfigFromFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select VPN Config File");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() ||
                        f.getName().toLowerCase().endsWith(".conf") ||
                        f.getName().toLowerCase().endsWith(".ovpn");
            }

            @Override
            public String getDescription() {
                return "VPN Config Files (*.conf, *.ovpn)";
            }
        });

        int result = fileChooser.showOpenDialog(panel);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                String content = new String(Files.readAllBytes(selectedFile.toPath()));
                configTextArea.setText(content);

                // Auto-detect type based on extension
                if (selectedFile.getName().toLowerCase().endsWith(".ovpn")) {
                    openVpnButton.setSelected(true);
                } else if (selectedFile.getName().toLowerCase().endsWith(".conf")) {
                    wireGuardButton.setSelected(true);
                }

                logger.info("Loaded config from: " + selectedFile.getAbsolutePath());
                validateConfig();
            } catch (Exception e) {
                logger.error("Failed to load config file: " + e.getMessage(), e);
                JOptionPane.showMessageDialog(panel,
                        "Failed to load config file:\n" + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void validateConfig() {
        String configText = configTextArea.getText();

        if (configText.trim().isEmpty()) {
            setValidationStatus("Config is empty", false);
            return;
        }

        com.wireup.vpn.VpnConfig config;
        if (wireGuardButton.isSelected()) {
            config = new WireGuardConfig(configText);
        } else {
            config = new com.wireup.vpn.OpenVpnConfig(configText);
        }

        if (config.isValid()) {
            setValidationStatus("✓ Config is valid - " + config.getSummary().split("\n")[0], true);
        } else {
            setValidationStatus("✗ " + config.getErrorMessage(), false);
        }
    }

    private void setValidationStatus(String message, boolean valid) {
        validationLabel.setText(message);
        validationLabel.setForeground(valid ? new Color(0, 128, 0) : Color.RED);
    }

    public String getConfigText() {
        return configTextArea.getText();
    }

    public com.wireup.vpn.VpnConfig.VpnType getVpnType() {
        return wireGuardButton.isSelected() ? com.wireup.vpn.VpnConfig.VpnType.WIREGUARD
                : com.wireup.vpn.VpnConfig.VpnType.OPENVPN;
    }

    private JPanel createAuthPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "OpenVPN Authentication (Optional)",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 11)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        Font labelFont = new Font("Arial", Font.PLAIN, 11);

        // Username
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        JLabel userLabel = new JLabel("Username:");
        userLabel.setFont(labelFont);
        panel.add(userLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        usernameField = new JTextField(20);
        usernameField.setFont(labelFont);
        panel.add(usernameField, gbc);

        // Password
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        JLabel passLabel = new JLabel("Password:");
        passLabel.setFont(labelFont);
        panel.add(passLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        passwordField = new JPasswordField(20);
        passwordField.setFont(labelFont);
        panel.add(passwordField, gbc);

        // TOTP/2FA Token
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        JLabel totpLabel = new JLabel("2FA/TOTP Token:");
        totpLabel.setFont(labelFont);
        totpLabel.setToolTipText("Leave empty if not using 2FA");
        panel.add(totpLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        totpField = new JTextField(10);
        totpField.setFont(labelFont);
        totpField.setToolTipText("Enter 6-digit code if required");
        panel.add(totpField, gbc);

        // Status label
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        authStatusLabel = new JLabel("Leave blank if your VPN doesn't require authentication");
        authStatusLabel.setFont(new Font("Arial", Font.ITALIC, 10));
        authStatusLabel.setForeground(Color.GRAY);
        panel.add(authStatusLabel, gbc);

        return panel;
    }

    public String getUsername() {
        return usernameField != null ? usernameField.getText().trim() : "";
    }

    public String getPassword() {
        if (passwordField == null)
            return "";

        char[] pass = passwordField.getPassword();
        String password = new String(pass);

        // Append TOTP token if provided (some VPNs require password+token)
        String totp = getTotpToken();
        if (!totp.isEmpty()) {
            password = password + totp;
        }

        // Clear the char array for security
        java.util.Arrays.fill(pass, '0');

        return password;
    }

    public String getTotpToken() {
        return totpField != null ? totpField.getText().trim() : "";
    }

    public boolean hasCredentials() {
        return getUsername() != null && !getUsername().isEmpty() &&
                getPassword() != null && !getPassword().isEmpty();
    }

    public JPanel getPanel() {
        return panel;
    }
}
