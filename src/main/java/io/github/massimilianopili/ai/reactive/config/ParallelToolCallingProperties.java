package io.github.massimilianopili.ai.reactive.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for parallel tool execution.
 *
 * <pre>
 * spring.ai.reactive-tools.parallel.enabled=true
 * spring.ai.reactive-tools.parallel.max-concurrency=10
 * spring.ai.reactive-tools.parallel.timeout-ms=30000
 * </pre>
 */
@ConfigurationProperties(prefix = "spring.ai.reactive-tools.parallel")
public class ParallelToolCallingProperties {

    /** Whether parallel tool execution is enabled. Default: false. */
    private boolean enabled = false;

    /** Maximum number of tool calls to execute concurrently. Default: 10. */
    private int maxConcurrency = 10;

    /** Overall timeout in milliseconds for parallel batch execution. Default: 30000. */
    private long timeoutMs = 30000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }

    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
}
