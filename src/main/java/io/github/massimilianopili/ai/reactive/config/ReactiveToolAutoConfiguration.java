package io.github.massimilianopili.ai.reactive.config;

import io.github.massimilianopili.ai.reactive.annotation.ReactiveTool;
import io.github.massimilianopili.ai.reactive.provider.ReactiveMethodToolCallbackProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spring Boot auto-configuration for reactive tools.
 *
 * <p>Automatically scans all Spring beans for methods annotated with {@link ReactiveTool}
 * and registers them as a {@link ToolCallbackProvider}.</p>
 *
 * <p>Enabled by default. Disable with {@code spring.ai.reactive-tools.enabled=false}.</p>
 */
@Configuration
@ConditionalOnClass(ToolCallbackProvider.class)
@ConditionalOnProperty(name = "spring.ai.reactive-tools.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ReactiveToolProperties.class)
public class ReactiveToolAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ReactiveToolAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(name = "reactiveToolCallbackProvider")
    public ToolCallbackProvider reactiveToolCallbackProvider(
            ApplicationContext context,
            ReactiveToolProperties props) {

        // Scan all beans for @ReactiveTool methods
        List<Object> beansWithReactiveTools = new ArrayList<>();

        for (String beanName : context.getBeanDefinitionNames()) {
            try {
                Object bean = context.getBean(beanName);
                if (hasReactiveToolMethods(bean)) {
                    beansWithReactiveTools.add(bean);
                }
            } catch (Exception e) {
                // Skip beans that can't be instantiated
            }
        }

        if (beansWithReactiveTools.isEmpty()) {
            log.debug("No beans with @ReactiveTool methods found");
            return () -> new org.springframework.ai.tool.ToolCallback[0];
        }

        log.info("Auto-configuring reactive tools from {} bean(s)", beansWithReactiveTools.size());

        return ReactiveMethodToolCallbackProvider.builder()
                .toolObjects(beansWithReactiveTools.toArray())
                .defaultTimeoutMs(props.getDefaultTimeoutMs())
                .build();
    }

    private boolean hasReactiveToolMethods(Object bean) {
        for (Method method : bean.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(ReactiveTool.class)) {
                return true;
            }
        }
        return false;
    }
}
