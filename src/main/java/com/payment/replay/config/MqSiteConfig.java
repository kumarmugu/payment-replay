package com.payment.replay.config;

/**
 * IBM MQ connection configuration for a single site.
 * Immutable after construction to ensure thread safety in connection management.
 */
public final class MqSiteConfig {

    private final String queueManager;
    private final String host;
    private final int port;
    private final String channel;
    private final String queueNamePrefix;
    private final int connectionTimeout;
    private final int retryCount;
    private final long retryDelayMs;
    private final String username;
    private final String password;

    private MqSiteConfig(Builder builder) {
        this.queueManager = builder.queueManager;
        this.host = builder.host;
        this.port = builder.port;
        this.channel = builder.channel;
        this.queueNamePrefix = builder.queueNamePrefix;
        this.connectionTimeout = builder.connectionTimeout;
        this.retryCount = builder.retryCount;
        this.retryDelayMs = builder.retryDelayMs;
        this.username = builder.username;
        this.password = builder.password;
    }

    public String getQueueManager() {
        return queueManager;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getChannel() {
        return channel;
    }

    public String getQueueNamePrefix() {
        return queueNamePrefix;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public long getRetryDelayMs() {
        return retryDelayMs;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Checks whether authentication credentials are configured.
     */
    public boolean hasCredentials() {
        return username != null && !username.isEmpty()
                && password != null && !password.isEmpty();
    }

    @Override
    public String toString() {
        return "MqSiteConfig{" +
                "queueManager='" + queueManager + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", channel='" + channel + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String queueManager = "";
        private String host = "localhost";
        private int port = 1414;
        private String channel = "";
        private String queueNamePrefix = "";
        private int connectionTimeout = 30000;
        private int retryCount = 3;
        private long retryDelayMs = 5000;
        private String username = "";
        private String password = "";

        private Builder() {
        }

        public Builder queueManager(String queueManager) {
            this.queueManager = queueManager;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder queueNamePrefix(String queueNamePrefix) {
            this.queueNamePrefix = queueNamePrefix;
            return this;
        }

        public Builder connectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public Builder retryDelayMs(long retryDelayMs) {
            this.retryDelayMs = retryDelayMs;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public MqSiteConfig build() {
            return new MqSiteConfig(this);
        }
    }
}
