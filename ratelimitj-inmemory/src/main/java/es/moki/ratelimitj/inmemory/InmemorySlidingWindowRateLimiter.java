package es.moki.ratelimitj.inmemory;

import es.moki.ratelimitj.core.api.LimitRule;
import es.moki.ratelimitj.core.time.TimeSupplier;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;


@ThreadSafe
public class InmemorySlidingWindowRateLimiter extends AbstractSlidingWindowRateLimiter {

    private final ConcurrentHashMap<String, Map<String, Long>> keys = new ConcurrentHashMap<>();
    private final Timer timer = new Timer(true);


    public InmemorySlidingWindowRateLimiter(Set<LimitRule> rules) {
        super(rules);
    }

    public InmemorySlidingWindowRateLimiter(Set<LimitRule> rules, TimeSupplier timeSupplier) {
        super(rules, timeSupplier);
    }

    @Override
    protected Map<String, Long> getMap(String key, int longestDuration) {

//        LoadingCache<String, Map<String, Long>> keys = Caffeine.newBuilder()
//            .maximumSize(10_000)
//            .expireAfterAccess(5, TimeUnit.MINUTES)
//            .refreshAfterWrite(1, TimeUnit.MINUTES)
//            .build(k -> new ConcurrentHashMap<>());
//
//        keys.get(key);
//        return keys.get(key);

        return keys.computeIfAbsent(key, s -> new ConcurrentHashMap<>());

    }
}
