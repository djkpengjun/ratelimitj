package es.moki.ratelimitj.redis;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.api.StatefulRedisConnection;
import es.moki.ratelimitj.core.LimitRule;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class RedisSlidingWindowRateLimiterTest {

    private static final Logger LOG = LoggerFactory.getLogger(RedisSlidingWindowRateLimiterTest.class);

    private static RedisClient client;
    private static StatefulRedisConnection<String, String> connect;

    @BeforeClass
    public static void before() {
        client = RedisClient.create("redis://localhost");
        connect = client.connect();
    }

    @AfterClass
    public static void after() {
        try(StatefulRedisConnection<String, String> connection = client.connect()) {
            connection.sync().flushdb();
        }
        connect.close();
        client.shutdown();
    }

    @Test
    public void shouldLimitSingleWindowAsync() throws Exception {

        ImmutableSet<LimitRule> rules = ImmutableSet.of(LimitRule.of(10, TimeUnit.SECONDS, 5));
        RedisSlidingWindowRateLimiter rateLimiter = new RedisSlidingWindowRateLimiter(connect, rules);

        List<CompletionStage> stageAsserts = new CopyOnWriteArrayList<>();
        Observable.defer(() -> Observable.just("ip:127.0.0.2"))
                .repeat(5)
                .subscribe((key) -> {

                    stageAsserts.add(rateLimiter.overLimitAsync(key)
                            .thenAccept(result -> assertThat(result).isFalse()));
                });

        for (CompletionStage stage : stageAsserts) {
            stage.toCompletableFuture().get();
        }

        assertThat(rateLimiter.overLimitAsync("ip:127.0.0.2").toCompletableFuture().get()).isTrue();
    }

    @Test
    public void shouldLimitDualWindowAsync() throws Exception {

        ImmutableSet<LimitRule> rules = ImmutableSet.of(LimitRule.of(2, TimeUnit.SECONDS, 5), LimitRule.of(10, TimeUnit.SECONDS, 20));
        RedisSlidingWindowRateLimiter rateLimiter = new RedisSlidingWindowRateLimiter(connect, rules);

        List<CompletionStage> stageAsserts = new CopyOnWriteArrayList<>();
        Observable.defer(() -> Observable.just("ip:127.0.0.10"))
                .repeat(5)
                .subscribe((key) -> {
                    LOG.debug("incrementing rate limiter");
                    stageAsserts.add(rateLimiter.overLimitAsync(key)
                            .thenAccept(result -> assertThat(result).isFalse()));
                });

        for (CompletionStage stage : stageAsserts) {
            stage.toCompletableFuture().get();
        }

        assertThat(rateLimiter.overLimitAsync("ip:127.0.0.10").toCompletableFuture().get()).isTrue();
        Thread.sleep(2000);
        assertThat(rateLimiter.overLimitAsync("ip:127.0.0.10").toCompletableFuture().get()).isFalse();
    }

    @Test
    public void shouldLimitSingleWindowSync() throws Exception {

        ImmutableSet<LimitRule> rules = ImmutableSet.of(LimitRule.of(10, TimeUnit.SECONDS, 5));
        RedisSlidingWindowRateLimiter rateLimiter = new RedisSlidingWindowRateLimiter(connect, rules);

        IntStream.rangeClosed(1, 5).forEach(value -> {
            assertThat(rateLimiter.overLimit("ip:127.0.0.5")).isFalse();
        });

        assertThat(rateLimiter.overLimit("ip:127.0.0.5")).isTrue();
    }

    @Test
    public void shouldWorkWithRedisTime() throws Exception {

        ImmutableSet<LimitRule> rules = ImmutableSet.of(LimitRule.of(10, TimeUnit.SECONDS, 5), LimitRule.of(3600, TimeUnit.SECONDS, 1000));
        RedisSlidingWindowRateLimiter rateLimiter = new RedisSlidingWindowRateLimiter(connect, rules, true);

        CompletionStage<Boolean> key = rateLimiter.overLimitAsync("ip:127.0.0.3");

        assertThat(key.toCompletableFuture().get()).isFalse();
    }

    @Test @Ignore
    public void shouldPerformFastSingleWindow() throws Exception {

        ImmutableSet<LimitRule> rules = ImmutableSet.of(LimitRule.of(1, TimeUnit.MINUTES, 100));
        RedisSlidingWindowRateLimiter rateLimiter = new RedisSlidingWindowRateLimiter(connect, rules);

        Stopwatch started = Stopwatch.createStarted();
        for(int i=1; i< 10000; i++){
            rateLimiter.overLimit("ip:127.0.0.11");
        }

        started.stop();
        System.out.println(started);
    }

}