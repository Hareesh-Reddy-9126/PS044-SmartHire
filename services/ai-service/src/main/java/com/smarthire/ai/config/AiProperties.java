package com.smarthire.ai.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * AI Service configuration properties.
 *
 * <p>Maps {@code smarthire.ai.*} properties from application.yml.
 */
@Component
@ConfigurationProperties(prefix = "smarthire.ai")
@Validated
public class AiProperties {

  private boolean enabled = true;
  private String provider = "mock";

  private JwtProperties jwt = new JwtProperties();
  private ClaudeProperties claude = new ClaudeProperties();

  // Getters and setters

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Spring-managed configuration properties object; not mutated after startup"
  )
  public JwtProperties getJwt() {
    return jwt;
  }

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring-injected configuration object; not mutated after injection"
  )
  public void setJwt(JwtProperties jwt) {
    this.jwt = jwt;
  }

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Spring-managed configuration properties object; not mutated after startup"
  )
  public ClaudeProperties getClaude() {
    return claude;
  }

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring-injected configuration object; not mutated after injection"
  )
  public void setClaude(ClaudeProperties claude) {
    this.claude = claude;
  }

  @Component
  @ConfigurationProperties(prefix = "smarthire.ai.jwt")
  public static class JwtProperties {
    @NotBlank private String jwkSetUri = "http://localhost:8082/oauth2/jwks";

    @NotBlank private String issuer = "https://smarthire.local/auth";

    @NotBlank private String audience = "smarthire";

    public String getJwkSetUri() {
      return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
      this.jwkSetUri = jwkSetUri;
    }

    public String getIssuer() {
      return issuer;
    }

    public void setIssuer(String issuer) {
      this.issuer = issuer;
    }

    public String getAudience() {
      return audience;
    }

    public void setAudience(String audience) {
      this.audience = audience;
    }
  }

  @Component
  @ConfigurationProperties(prefix = "smarthire.ai.claude")
  public static class ClaudeProperties {
    private String model = "claude-3-haiku-20240307";
    private int maxTokens = 1024;
    private double temperature = 0.1;
    private String apiKey = "";

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public int getMaxTokens() {
      return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
      this.maxTokens = maxTokens;
    }

    public double getTemperature() {
      return temperature;
    }

    public void setTemperature(double temperature) {
      this.temperature = temperature;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }
  }
}
