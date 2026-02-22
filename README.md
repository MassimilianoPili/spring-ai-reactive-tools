# Spring AI Reactive Tools

Reactive tool support for Spring AI MCP servers. Write `@ReactiveTool` methods returning `Mono<T>` or `Flux<T>` without calling `.block()`.

## Motivation

Spring AI's `@Tool` annotation does not support reactive return types ([#1778](https://github.com/spring-projects/spring-ai/issues/1778), [#4434](https://github.com/spring-projects/spring-ai/issues/4434), [#2761](https://github.com/spring-projects/spring-ai/issues/2761)). If your MCP tool needs to call a reactive API (WebClient, R2DBC, reactive MongoDB, etc.), you're forced to `.block()` inside the method body, losing the benefits of reactive programming and risking thread starvation.

This library provides a temporary bridge until Spring AI adds native reactive support to `@Tool`. It integrates with the existing Spring AI ecosystem (`ToolCallback`, `ToolCallbackProvider`), so migration will be straightforward when native support lands.

## The Solution

`@ReactiveTool` lets you write fully reactive tool methods. The library handles the blocking bridge at the boundary — subscribing on `Schedulers.boundedElastic()` with configurable timeouts — so your tool code stays non-blocking.

Supported return types:
- `Mono<T>` — single async result
- `Flux<T>` — collected into a List before serialization
- Any synchronous type — wrapped in `Mono.just()` automatically

## Installation

```xml
<dependency>
    <groupId>io.github.massimilianopili</groupId>
    <artifactId>spring-ai-reactive-tools</artifactId>
    <version>0.1.0</version>
</dependency>
```

Requires Java 17+ and Spring AI 1.0.0+.

## Quick Start

### 1. Annotate your methods

```java
@Component
public class MyTools {

    private final WebClient webClient;

    public MyTools(WebClient webClient) {
        this.webClient = webClient;
    }

    @ReactiveTool(name = "fetch_data", description = "Fetches data from an API endpoint")
    public Mono<Map<String, Object>> fetchData(
            @ToolParam(description = "The URL to fetch") String url) {
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<>() {});
    }

    @ReactiveTool(name = "search_items", description = "Searches items by query")
    public Flux<Item> searchItems(
            @ToolParam(description = "Search query") String query,
            @ToolParam(description = "Max results", required = false) Integer limit) {
        return itemRepository.findByQuery(query)
                .take(limit != null ? limit : 10);
    }
}
```

### 2. Enable auto-configuration

With Spring Boot, reactive tools are discovered automatically. No extra annotation needed — any Spring bean with `@ReactiveTool` methods is picked up.

Alternatively, use `@EnableReactiveTools` on a `@Configuration` class:

```java
@SpringBootApplication
@EnableReactiveTools
public class MyMcpServer {
    public static void main(String[] args) {
        SpringApplication.run(MyMcpServer.class, args);
    }
}
```

### 3. Or register manually

```java
@Bean
public ToolCallbackProvider reactiveTools(MyTools tools) {
    return ReactiveMethodToolCallbackProvider.builder()
            .toolObjects(tools)
            .defaultTimeout(Duration.ofSeconds(60))
            .build();
}
```

## Configuration

```properties
# Enable/disable auto-configuration (default: true)
spring.ai.reactive-tools.enabled=true

# Default timeout for all reactive tools in ms (default: 30000)
spring.ai.reactive-tools.default-timeout-ms=30000
```

Per-tool timeout can be set via the annotation:

```java
@ReactiveTool(name = "slow_operation", description = "...", timeoutMs = 60000)
public Mono<String> slowOperation() { ... }
```

## How It Works

1. `@ReactiveTool` methods are scanned and wrapped in `ReactiveToolCallbackAdapter`
2. The adapter implements Spring AI's `ToolCallback` interface
3. When the MCP server calls `call(String)`, the adapter:
   - Invokes the method via reflection
   - If the result is `Mono` or `Flux`, subscribes on `boundedElastic`
   - Blocks with the configured timeout
   - Serializes the result to JSON
4. Error handling returns a JSON error object instead of throwing

## Requirements

- Java 17+
- Spring AI 1.0.0+
- Spring Boot 3.4+ (for auto-configuration)
- Project Reactor (transitive)

## License

[Apache License 2.0](LICENSE)
