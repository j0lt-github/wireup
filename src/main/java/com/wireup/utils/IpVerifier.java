package com.wireup.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

/**
 * Utility to verify external IP address
 */
public class IpVerifier {

    private static final String IP_CHECK_URL = "https://api.ipify.org?format=text";
    private static final int TIMEOUT_MS = 30000; // Increased to 30 seconds for WireGuard connection

    /**
     * Get current external IP address without proxy
     */
    public static String getCurrentIp() {
        try {
            URL url = java.net.URI.create(IP_CHECK_URL).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String ip = reader.readLine();
                return ip != null ? ip.trim() : "Unknown";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Get external IP address through SOCKS5 proxy
     */
    public static String getIpThroughProxy(String proxyHost, int proxyPort) {
        try {
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, proxyPort));

            URL url = java.net.URI.create(IP_CHECK_URL).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String ip = reader.readLine();
                return ip != null ? ip.trim() : "Unknown";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Verify if two IPs are different
     */
    public static boolean areIpsDifferent(String ip1, String ip2) {
        if (ip1 == null || ip2 == null)
            return false;
        if (ip1.startsWith("Error") || ip2.startsWith("Error"))
            return false;
        if (ip1.equals("Unknown") || ip2.equals("Unknown"))
            return false;

        return !ip1.equals(ip2);
    }
}
