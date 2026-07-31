package com.payment.replay.mq;

import com.ibm.mq.MQEnvironment;
import com.ibm.mq.MQException;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.MQConstants;
import com.payment.replay.config.AppConfig;
import com.payment.replay.config.MqSiteConfig;
import com.payment.replay.exception.MqPublishException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages IBM MQ connections for both site 1 and site 2.
 *
 * Responsibilities:
 * - Create and cache connections per site
 * - Retry connection on failure with exponential backoff
 * - Recover from broken connections
 * - Provide thread-safe access to queue managers
 * - Clean up resources on shutdown
 */
public final class MqConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(MqConnectionManager.class);

    private final AppConfig config;
    private final ConcurrentHashMap<Integer, MQQueueManager> connections = new ConcurrentHashMap<>();
    private final Object site1Lock = new Object();
    private final Object site2Lock = new Object();

    public MqConnectionManager(AppConfig config) {
        this.config = config;
    }

    /**
     * Gets or creates a connection to the specified MQ site.
     * Thread-safe - concurrent calls for the same site will share a single connection.
     *
     * @param siteNumber 1 or 2
     * @return active MQQueueManager for the requested site
     * @throws MqPublishException if connection cannot be established after retries
     */
    public MQQueueManager getConnection(int siteNumber) {
        MQQueueManager existing = connections.get(siteNumber);

        if (existing != null && isConnectionValid(existing)) {
            return existing;
        }

        // Need to create or recreate connection
        Object lock = (siteNumber == 1) ? site1Lock : site2Lock;
        synchronized (lock) {
            // Double-check after acquiring lock
            existing = connections.get(siteNumber);
            if (existing != null && isConnectionValid(existing)) {
                return existing;
            }

            // Close stale connection if any
            if (existing != null) {
                closeQuietly(existing, siteNumber);
            }

            // Create new connection with retry
            MqSiteConfig siteConfig = config.getMqConfigForSite(siteNumber);
            MQQueueManager newConnection = connectWithRetry(siteConfig, siteNumber);
            connections.put(siteNumber, newConnection);
            return newConnection;
        }
    }

    /**
     * Attempts to connect to MQ with retry logic and exponential backoff.
     *
     * @param siteConfig MQ site configuration
     * @param siteNumber site identifier for logging
     * @return connected MQQueueManager
     * @throws MqPublishException if all retry attempts fail
     */
    private MQQueueManager connectWithRetry(MqSiteConfig siteConfig, int siteNumber) {
        int maxRetries = siteConfig.getRetryCount();
        long delayMs = siteConfig.getRetryDelayMs();
        MQException lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("Connecting to MQ site {} (attempt {}/{}): {}:{}/{}",
                        siteNumber, attempt, maxRetries,
                        siteConfig.getHost(), siteConfig.getPort(), siteConfig.getQueueManager());

                MQQueueManager queueManager = createConnection(siteConfig);

                log.info("Successfully connected to MQ site {}: {}", siteNumber, siteConfig.getQueueManager());
                return queueManager;

            } catch (MQException e) {
                lastException = e;
                log.warn("MQ connection attempt {}/{} failed for site {}: RC={}, Reason={}",
                        attempt, maxRetries, siteNumber,
                        e.completionCode, e.reasonCode, e);

                if (attempt < maxRetries) {
                    try {
                        long sleepMs = delayMs * (long) Math.pow(2, attempt - 1);
                        log.info("Waiting {}ms before retry...", sleepMs);
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new MqPublishException(
                                "Connection interrupted during retry",
                                siteConfig.getQueueManager(), siteNumber, ie);
                    }
                }
            }
        }

        throw new MqPublishException(
                String.format("Failed to connect to MQ site %d after %d attempts. Last error: RC=%d",
                        siteNumber, maxRetries,
                        lastException != null ? lastException.reasonCode : -1),
                siteConfig.getQueueManager(), siteNumber, lastException);
    }

    /**
     * Creates a new MQ connection using the IBM MQ client API.
     *
     * @param siteConfig connection parameters
     * @return connected MQQueueManager
     * @throws MQException if connection fails
     */
    @SuppressWarnings("unchecked")
    private MQQueueManager createConnection(MqSiteConfig siteConfig) throws MQException {
        Hashtable<String, Object> properties = new Hashtable<>();

        properties.put(MQConstants.HOST_NAME_PROPERTY, siteConfig.getHost());
        properties.put(MQConstants.PORT_PROPERTY, siteConfig.getPort());
        properties.put(MQConstants.CHANNEL_PROPERTY, siteConfig.getChannel());
        properties.put(MQConstants.TRANSPORT_PROPERTY, MQConstants.TRANSPORT_MQSERIES_CLIENT);

        // Authentication if configured
        if (siteConfig.hasCredentials()) {
            properties.put(MQConstants.USER_ID_PROPERTY, siteConfig.getUsername());
            properties.put(MQConstants.PASSWORD_PROPERTY, siteConfig.getPassword());
            properties.put(MQConstants.USE_MQCSP_AUTHENTICATION_PROPERTY, true);
        }

        return new MQQueueManager(siteConfig.getQueueManager(), properties);
    }

    /**
     * Checks if an existing connection is still valid/active.
     *
     * @param queueManager the connection to check
     * @return true if connection is usable
     */
    private boolean isConnectionValid(MQQueueManager queueManager) {
        try {
            return queueManager.isConnected();
        } catch (Exception e) {
            log.debug("Connection validity check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Invalidates and removes a connection for a specific site.
     * Called when a send operation fails, triggering reconnection on next use.
     *
     * @param siteNumber the site whose connection should be invalidated
     */
    public void invalidateConnection(int siteNumber) {
        MQQueueManager removed = connections.remove(siteNumber);
        if (removed != null) {
            closeQuietly(removed, siteNumber);
            log.info("Invalidated MQ connection for site {}", siteNumber);
        }
    }

    /**
     * Closes all connections. Should be called during application shutdown.
     */
    public void closeAll() {
        log.info("Closing all MQ connections...");
        for (java.util.Map.Entry<Integer, MQQueueManager> entry : connections.entrySet()) {
            closeQuietly(entry.getValue(), entry.getKey());
        }
        connections.clear();
        log.info("All MQ connections closed");
    }

    /**
     * Closes a queue manager connection without throwing exceptions.
     */
    private void closeQuietly(MQQueueManager queueManager, int siteNumber) {
        try {
            if (queueManager != null && queueManager.isConnected()) {
                queueManager.disconnect();
                log.debug("Disconnected from MQ site {}", siteNumber);
            }
        } catch (MQException e) {
            log.warn("Error closing MQ connection for site {}: RC={}", siteNumber, e.reasonCode);
        }
    }
}
