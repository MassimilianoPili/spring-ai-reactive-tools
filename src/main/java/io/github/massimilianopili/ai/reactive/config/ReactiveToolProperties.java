package io.github.massimilianopili.ai.reactive.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for spring-ai-reactive-tools.
 *
 * <pre>
 * spring.ai.reactive-tools.default-timeout-ms=30000
 * spring.ai.reactive-tools.enabled=true
 * </pre>
 */
@ConfigurationProperties(prefix = "spring.ai.reactive-tools")
public class ReactiveToolProperties {

    /** Whether auto-configuration of reactive tools is enabled. Default: true. */
    private boolean enabled = true;

    /** Default timeout in milliseconds for reactive tools. Default: 30000. */
    private long defaultTimeoutMs = 30000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getDefaultTimeoutMs() { return defaultTimeoutMs; }
    public void setDefaultTimeoutMs(long defaultTimeoutMs) { this.defaultTimeoutMs = defaultTimeoutMs; }
}
