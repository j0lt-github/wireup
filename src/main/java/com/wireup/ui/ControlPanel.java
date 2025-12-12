package com.wireup.ui;

import com.wireup.utils.Logger;
import com.wireup.vpn.ConnectionManager;
import com.wireup.vpn.WireGuardConfig;

import javax.swing.*;
import java.awt.*;

/**
 * Panel with VPN control buttons
 */
public class ControlPanel {

    private ConnectionManager connectionManager;
    private final Logger logger;

    private JPanel panel;
    private JButton connectButton;
    private JButton disconnectButton;
    private JButton reconnectButton;
    private JButton viewLogsButton;
    private ConfigPanel configPanel;

    public ControlPanel(ConnectionManager connectionManager, Logger logger) {
        this.connectionManager = connectionManager;
        this.logger = logger;

        initializeUI();
    }

    /**
     * Set or update the connection manager
     */
    public void setConnectionManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    private void initializeUI() {
        panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));

        // Connect button
        connectButton = new JButton("Connect to VPN");
        connectButton.setFont(new Font("Arial", Font.BOLD, 12));
        connectButton.setForeground(new Color(0, 128, 0));
        connectButton.addActionListener(e -> onConnect());

        // Disconnect button
        disconnectButton = new JButton("Disconnect");
        disconnectButton.setEnabled(false);
        disconnectButton.addActionListener(e -> onDisconnect());

        // Reconnect button
        reconnectButton = new JButton("Reconnect");
        reconnectButton.setEnabled(false);
        reconnectButton.addActionListener(e -> onReconnect());

        // View logs button
        viewLogsButton = new JButton("View Logs");
        viewLogsButton.addActionListener(e -> onViewLogs());

        panel.add(connectButton);
        panel.add(disconnectButton);
        panel.add(reconnectButton);
        panel.add(viewLogsButton);
    }

    public void setConfigPanel(ConfigPanel configPanel) {
        this.configPanel = configPanel;
    }

    private void onConnect() {
        // Check if connection manager is available
        if (connectionManager == null) {
            JOptionPane.showMessageDialog(panel,
                    "VPN features are not available.\n" +
                            "Docker may not be running or failed to initialize.\n" +
                            "Check the extension logs for details.",
                    "VPN Not Available",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Validate that we have a config panel reference
        if (configPanel == null) {
            JOptionPane.showMessageDialog(panel,
                    "Configuration panel not available",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Get config from the config panel
        String configText = configPanel.getConfigText();
        com.wireup.vpn.VpnConfig.VpnType vpnType = configPanel.getVpnType();

        if (configText == null || configText.trim().isEmpty() || configText.contains("# Paste your WireGuard")) {
            JOptionPane.showMessageDialog(panel,
                    "Please enter a valid configuration first",
                    "No Configuration",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Validate config
        com.wireup.vpn.VpnConfig config;
        if (vpnType == com.wireup.vpn.VpnConfig.VpnType.WIREGUARD) {
            config = new WireGuardConfig(configText);
        } else {
            config = new com.wireup.vpn.OpenVpnConfig(configText);

            // Set credentials if provided for OpenVPN
            com.wireup.vpn.OpenVpnConfig ovpnConfig = (com.wireup.vpn.OpenVpnConfig) config;
            String username = configPanel.getUsername();
            String password = configPanel.getPassword();

            if (username != null && !username.isEmpty() &&
                    password != null && !password.isEmpty()) {
                ovpnConfig.setCredentials(username, password);
                logger.info("OpenVPN credentials provided for authentication");
            } else if (ovpnConfig.requiresAuth() && !ovpnConfig.hasCredentials()) {
                logger.warn("OpenVPN config requires authentication but no credentials provided");
            }
        }

        if (!config.isValid()) {
            JOptionPane.showMessageDialog(panel,
                    "Invalid configuration:\n" + config.getErrorMessage(),
                    "Validation Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Connect
        logger.info("User initiated connection (" + vpnType + ")");
        connectionManager.connect(config);
    }

    private void onDisconnect() {
        // Show confirmation dialog
        int choice = JOptionPane.showConfirmDialog(panel,
                "Are you sure you want to disconnect from the VPN?\n" +
                        "This will stop routing traffic through the VPN tunnel.",
                "Confirm Disconnect",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        logger.info("User confirmed disconnect");

        // Disable button immediately for responsiveness
        disconnectButton.setEnabled(false);
        disconnectButton.setText("Disconnecting...");

        // Run disconnect in background thread to keep UI responsive
        new Thread(() -> {
            try {
                connectionManager.disconnect();
            } finally {
                // Reset button text on EDT
                SwingUtilities.invokeLater(() -> {
                    disconnectButton.setText("Disconnect");
                });
            }
        }).start();
    }

    private void onReconnect() {
        logger.info("User initiated reconnect");
        connectionManager.reconnect();
    }

    private void onViewLogs() {
        // Show extension logs
        JOptionPane.showMessageDialog(panel,
                "Check the Burp Suite 'Extensions' tab for logs.\n" +
                        "Look for '[WireUp]' entries in the Output/Errors tabs.",
                "View Logs",
                JOptionPane.INFORMATION_MESSAGE);
    }

    public void updateButtons(ConnectionManager.ConnectionState state) {
        switch (state) {
            case DISCONNECTED:
                connectButton.setEnabled(true);
                disconnectButton.setEnabled(false);
                reconnectButton.setEnabled(false);
                break;

            case CONNECTING:
                connectButton.setEnabled(false);
                disconnectButton.setEnabled(true);
                reconnectButton.setEnabled(false);
                break;

            case CONNECTED:
                connectButton.setEnabled(false);
                disconnectButton.setEnabled(true);
                reconnectButton.setEnabled(true);
                break;

            case ERROR:
                connectButton.setEnabled(true);
                disconnectButton.setEnabled(true);
                reconnectButton.setEnabled(true);
                break;
        }
    }

    public JPanel getPanel() {
        return panel;
    }
}
