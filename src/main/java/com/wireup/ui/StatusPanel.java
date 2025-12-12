package com.wireup.ui;

import com.wireup.vpn.ConnectionManager;
import com.wireup.utils.IpVerifier;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * Panel to display connection status and information
 */
public class StatusPanel {

    private JPanel panel;
    private JLabel stateLabel;
    private JLabel hostIpLabel;
    private JLabel vpnIpLabel;
    private JLabel containerLabel;
    private JLabel proxyConfigLabel;
    private JPanel statusIndicator;

    public StatusPanel() {
        initializeUI();
        updateHostIp();
    }

    private void initializeUI() {
        panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Connection Status",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 12)));

        // Status indicator (colored box)
        statusIndicator = new JPanel();
        statusIndicator.setPreferredSize(new Dimension(15, 15));
        statusIndicator.setBackground(Color.RED);
        statusIndicator.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));

        // Info panel
        JPanel infoPanel = new JPanel(new GridLayout(5, 2, 10, 5));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Labels
        Font labelFont = new Font("Arial", Font.PLAIN, 11);
        Font valueFont = new Font("Arial", Font.BOLD, 11);

        JLabel stateTitle = new JLabel("Status:");
        stateTitle.setFont(labelFont);
        stateLabel = new JLabel("Disconnected");
        stateLabel.setFont(valueFont);

        JLabel hostIpTitle = new JLabel("Host IP:");
        hostIpTitle.setFont(labelFont);
        hostIpLabel = new JLabel("Checking...");
        hostIpLabel.setFont(valueFont);
        hostIpLabel.setForeground(Color.BLUE);

        JLabel vpnIpTitle = new JLabel("VPN IP:");
        vpnIpTitle.setFont(labelFont);
        vpnIpLabel = new JLabel("Not connected");
        vpnIpLabel.setFont(valueFont);
        vpnIpLabel.setForeground(Color.GRAY);

        JLabel containerTitle = new JLabel("Container:");
        containerTitle.setFont(labelFont);
        containerLabel = new JLabel("Not running");
        containerLabel.setFont(valueFont);

        JLabel proxyTitle = new JLabel("Burp Proxy:");
        proxyTitle.setFont(labelFont);
        proxyConfigLabel = new JLabel("Not configured");
        proxyConfigLabel.setFont(valueFont);

        infoPanel.add(stateTitle);
        infoPanel.add(stateLabel);
        infoPanel.add(hostIpTitle);
        infoPanel.add(hostIpLabel);
        infoPanel.add(vpnIpTitle);
        infoPanel.add(vpnIpLabel);
        infoPanel.add(containerTitle);
        infoPanel.add(containerLabel);
        infoPanel.add(proxyTitle);
        infoPanel.add(proxyConfigLabel);

        // Left panel with indicator
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        leftPanel.add(statusIndicator);

        // Credit footer
        JLabel creditLabel = new JLabel("Made by j0lt");
        creditLabel.setFont(new Font("Arial", Font.ITALIC, 9));
        creditLabel.setForeground(Color.GRAY);
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footerPanel.add(creditLabel);

        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(infoPanel, BorderLayout.CENTER);
        panel.add(footerPanel, BorderLayout.SOUTH);
    }

    public void updateStatus(ConnectionManager.ConnectionState state, ConnectionManager manager) {
        switch (state) {
            case DISCONNECTED:
                statusIndicator.setBackground(Color.RED);
                stateLabel.setText("Disconnected");
                stateLabel.setForeground(Color.BLACK);
                vpnIpLabel.setText("Not connected");
                vpnIpLabel.setForeground(Color.GRAY);
                containerLabel.setText("Not running");
                proxyConfigLabel.setText("Not configured");
                updateHostIp();
                break;

            case CONNECTING:
                statusIndicator.setBackground(Color.ORANGE);
                stateLabel.setText("Connecting...");
                stateLabel.setForeground(Color.ORANGE);
                containerLabel.setText("Starting...");
                proxyConfigLabel.setText("Configuring...");
                break;

            case CONNECTED:
                statusIndicator.setBackground(Color.GREEN);
                stateLabel.setText("Connected");
                stateLabel.setForeground(new Color(0, 128, 0));

                if (manager.getVpnIp() != null) {
                    vpnIpLabel.setText(manager.getVpnIp());
                    vpnIpLabel.setForeground(new Color(0, 128, 0));
                }

                containerLabel.setText("Running");
                proxyConfigLabel.setText("127.0.0.1:1080 (SOCKS5)");
                break;

            case ERROR:
                statusIndicator.setBackground(Color.RED);
                stateLabel.setText(
                        "Error: " + (manager.getErrorMessage() != null ? manager.getErrorMessage() : "Unknown error"));
                stateLabel.setForeground(Color.RED);
                containerLabel.setText("Failed");
                break;
        }
    }

    private void updateHostIp() {
        new Thread(() -> {
            String ip = IpVerifier.getCurrentIp();
            SwingUtilities.invokeLater(() -> {
                hostIpLabel.setText(ip);
            });
        }, "WireUp-HostIpCheck").start();
    }

    public JPanel getPanel() {
        return panel;
    }
}
