package io.github.massimilianopili.ai.reactive.annotation;

import io.github.massimilianopili.ai.reactive.config.ReactiveToolAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables auto-scanning and registration of {@link ReactiveTool} methods.
 *
 * <p>Add this annotation to your Spring Boot application class or any
 * {@code @Configuration} class to automatically discover and register
 * all beans containing {@link ReactiveTool}-annotated methods.</p>
 *
 * <pre>{@code
 * @SpringBootApplication
 * @EnableReactiveTools
 * public class MyMcpServer {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyMcpServer.class, args);
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(ReactiveToolAutoConfiguration.class)
public @interface EnableReactiveTools {
}
