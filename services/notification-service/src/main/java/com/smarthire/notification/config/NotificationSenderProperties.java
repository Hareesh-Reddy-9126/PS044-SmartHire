package com.smarthire.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the notification delivery channel.
 *
 * <p>Bound from {@code smarthire.notification.sender.*}. The {@code logging} sender is used for
 * local development and integration tests; {@code smtp} delivers real email in production.
 */
@ConfigurationProperties(prefix = "smarthire.notification.sender")
public class NotificationSenderProperties {

  /** Delivery channel: {@code logging}, {@code smtp} or {@code resend}. */
  private String type = "logging";

  /** Retry policy for transient delivery failures. */
  private int maxAttempts = 3;

  /** Backoff between delivery attempts, in milliseconds. */
  private long retryBackoffMs = 500;

  private Smtp smtp = new Smtp();

  private Resend resend = new Resend();

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  public long getRetryBackoffMs() {
    return retryBackoffMs;
  }

  public void setRetryBackoffMs(long retryBackoffMs) {
    this.retryBackoffMs = retryBackoffMs;
  }

  public Smtp getSmtp() {
    return smtp;
  }

  public void setSmtp(Smtp smtp) {
    this.smtp = smtp;
  }

  public Resend getResend() {
    return resend;
  }

  public void setResend(Resend resend) {
    this.resend = resend;
  }

  /** SMTP transport settings. Values are supplied by the deployment environment. */
  public static class Smtp {

    private String host = "";

    private int port = 587;

    private String username = "";

    private String password = "";

    private String from = "";

    private boolean starttls = true;

    private boolean ssl = false;

    private int connectionTimeoutMs = 5_000;

    private int timeoutMs = 10_000;

    private int writeTimeoutMs = 10_000;

    public String getHost() {
      return host;
    }

    public void setHost(String host) {
      this.host = host;
    }

    public int getPort() {
      return port;
    }

    public void setPort(int port) {
      this.port = port;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public String getFrom() {
      return from;
    }

    public void setFrom(String from) {
      this.from = from;
    }

    public boolean isStarttls() {
      return starttls;
    }

    public void setStarttls(boolean starttls) {
      this.starttls = starttls;
    }

    public boolean isSsl() {
      return ssl;
    }

    public void setSsl(boolean ssl) {
      this.ssl = ssl;
    }

    public int getConnectionTimeoutMs() {
      return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(int connectionTimeoutMs) {
      this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public int getTimeoutMs() {
      return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
      this.timeoutMs = timeoutMs;
    }

    public int getWriteTimeoutMs() {
      return writeTimeoutMs;
    }

    public void setWriteTimeoutMs(int writeTimeoutMs) {
      this.writeTimeoutMs = writeTimeoutMs;
    }
  }

  /**
   * Resend HTTP API settings.
   *
   * <p>{@code from} must be an address Resend is allowed to send for: the shared
   * {@code onboarding@resend.dev} sandbox (which only delivers to the account owner) or an address
   * on a domain verified in the Resend dashboard.
   */
  public static class Resend {

    private String apiKey = "";

    private String from = "";

    private String baseUrl = "https://api.resend.com";

    private int connectTimeoutMs = 5_000;

    private int readTimeoutMs = 10_000;

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getFrom() {
      return from;
    }

    public void setFrom(String from) {
      this.from = from;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutMs() {
      return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
      this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
      return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
      this.readTimeoutMs = readTimeoutMs;
    }
  }
}
