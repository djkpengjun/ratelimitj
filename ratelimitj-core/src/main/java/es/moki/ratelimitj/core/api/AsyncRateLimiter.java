package es.moki.ratelimitj.core.api;


import java.util.concurrent.CompletionStage;

/**
 * An asynchronous rate limiter interface supporting Java 8's {@link java.util.concurrent.CompletionStage}.
 */
public interface AsyncRateLimiter {

    CompletionStage<Boolean> overLimitAsync(String key);

    CompletionStage<Boolean> overLimitAsync(String key, int weight);
}
