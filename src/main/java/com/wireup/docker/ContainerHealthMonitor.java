package com.wireup.docker;

import com.wireup.utils.Logger;

import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

/**
 * Monitors container health and triggers callbacks on status changes
 */
public class ContainerHealthMonitor {

    private static final long CHECK_INTERVAL_MS = 5000; // 5 seconds

    private final DockerManager dockerManager;
    private final Logger logger;
    private Timer timer;
    private boolean lastKnownState;
    private Consumer<Boolean> statusChangeCallback;

    public ContainerHealthMonitor(DockerManager dockerManager, Logger logger) {
        this.dockerManager = dockerManager;
        this.logger = logger;
        this.lastKnownState = false;
    }

    /**
     * Start monitoring with a callback for status changes
     */
    public void startMonitoring(Consumer<Boolean> statusChangeCallback) {
        this.statusChangeCallback = statusChangeCallback;

        if (timer != null) {
            timer.cancel();
        }

        timer = new Timer("WireUp-HealthMonitor", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkHealth();
            }
        }, 0, CHECK_INTERVAL_MS);

        logger.debug("Health monitoring started");
    }

    /**
     * Stop monitoring
     */
    public void stopMonitoring() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        logger.debug("Health monitoring stopped");
    }

    private void checkHealth() {
        try {
            boolean currentState = dockerManager.isContainerRunning();

            // Only trigger callback if state changed
            if (currentState != lastKnownState) {
                logger.info("Container state changed: " + (currentState ? "RUNNING" : "STOPPED"));
                lastKnownState = currentState;

                if (statusChangeCallback != null) {
                    statusChangeCallback.accept(currentState);
                }
            }
        } catch (Exception e) {
            logger.debug("Error during health check: " + e.getMessage());
        }
    }
}
