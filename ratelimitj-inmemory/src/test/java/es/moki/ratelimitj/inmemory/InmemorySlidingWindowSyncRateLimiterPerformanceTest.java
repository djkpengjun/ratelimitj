package es.moki.ratelimitj.inmemory;

import es.moki.ratelimitj.core.api.LimitRule;
import es.moki.ratelimitj.core.api.RateLimiter;
import es.moki.ratelimitj.core.time.TimeSupplier;
import es.moki.ratelimitj.internal.test.AbstractSyncRateLimiterPerformanceTest;

import java.util.Set;

public class InmemorySlidingWindowSyncRateLimiterPerformanceTest extends AbstractSyncRateLimiterPerformanceTest {

    @Override
    protected RateLimiter getRateLimiter(Set<LimitRule> rules, TimeSupplier timeSupplier) {
        return new InmemorySlidingWindowRateLimiter(rules, timeSupplier);
    }
}
