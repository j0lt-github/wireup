package com.wireup.utils;

public interface VpnLogger {
    void debug(String message);

    void info(String message);

    void warn(String message);

    void error(String message);

    void error(String message, Throwable throwable);

    void securityInfo(String message);
}
