package com.wireup.ui;

import burp.api.montoya.MontoyaApi;
import com.wireup.utils.Logger;
import com.wireup.vpn.ConnectionManager;

import javax.swing.*;
import java.awt.*;

/**
 * Main UI tab for WireUp extension
 */
public class WireUpTab {

    private final MontoyaApi api;
    private ConnectionManager connectionManager;
    private final Logger logger;

    private JPanel mainPanel;
    private ConfigPanel configPanel;
    private StatusPanel statusPanel;
    private ControlPanel controlPanel;

    public WireUpTab(MontoyaApi api, ConnectionManager connectionManager, Logger logger) {
        this.api = api;
        this.connectionManager = connectionManager;
        this.logger = logger;

        initializeUI();

        // Listen for connection state changes (only if manager available)
        if (connectionManager != null) {
            connectionManager.addStateChangeListener(state -> {
                SwingUtilities.invokeLater(() -> {
                    statusPanel.updateStatus(state, connectionManager);
                    controlPanel.updateButtons(state);
                });
            });
        }
    }

    /**
     * Set or update the connection manager (called after Docker initialization)
     */
    public void setConnectionManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;

        // Update control panel reference
        if (controlPanel != null) {
            controlPanel.setConnectionManager(connectionManager);
        }

        // Set up state change listener
        if (connectionManager != null) {
            connectionManager.addStateChangeListener(state -> {
                SwingUtilities.invokeLater(() -> {
                    statusPanel.updateStatus(state, connectionManager);
                    controlPanel.updateButtons(state);
                });
            });
        }
    }

    private void initializeUI() {
        mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create sub-panels
        controlPanel = new ControlPanel(connectionManager, logger);
        configPanel = new ConfigPanel(connectionManager, logger);
        controlPanel.setConfigPanel(configPanel);
        statusPanel = new StatusPanel();

        // Create header with logo and title
        JPanel headerPanel = createHeader();

        // Layout panels
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(configPanel.getPanel(), BorderLayout.CENTER);

        // Create bottom panel with control and status
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.add(controlPanel.getPanel(), BorderLayout.NORTH);
        bottomPanel.add(statusPanel.getPanel(), BorderLayout.CENTER);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JLabel titleLabel = new JLabel("WireUp - VPN Proxy for Burp Suite");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));

        JLabel subtitleLabel = new JLabel("Route traffic through OpenVPN or WireGuard tunnels via Docker");
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        subtitleLabel.setForeground(Color.GRAY);

        JLabel versionLabel = new JLabel("v1.2.0");
        versionLabel.setFont(new Font("Arial", Font.PLAIN, 10));
        versionLabel.setForeground(Color.LIGHT_GRAY);
        versionLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        JPanel titlePanel = new JPanel(new GridLayout(2, 1));
        titlePanel.add(titleLabel);
        titlePanel.add(subtitleLabel);

        header.add(titlePanel, BorderLayout.WEST);
        header.add(versionLabel, BorderLayout.EAST);

        return header;
    }

    public Component getComponent() {
        return mainPanel;
    }
}
