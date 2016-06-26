package es.moki.ratelimitj.inmemory;

import es.moki.ratelimitj.api.LimitRule;
import es.moki.ratelimitj.api.RateLimiter;
import es.moki.ratelimitj.core.time.SystemTimeSupplier;
import es.moki.ratelimitj.core.time.TimeSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static es.moki.ratelimitj.core.RateLimitUtils.coalesce;
import static java.util.Objects.requireNonNull;


public abstract class AbstractSlidingWindowRateLimiter implements RateLimiter {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private final Set<LimitRule> rules;
    private final TimeSupplier timeSupplier;

    public AbstractSlidingWindowRateLimiter(Set<LimitRule> rules) {
        this(rules, new SystemTimeSupplier());
    }

    public AbstractSlidingWindowRateLimiter(Set<LimitRule> rules, TimeSupplier timeSupplier) {
        this.rules = rules;
        this.timeSupplier = timeSupplier;
    }

    @Override
    public boolean overLimit(String key) {
        return overLimit(key, 1);
    }

    @Override
    public boolean overLimit(String key, int weight) {

        requireNonNull(key, "key cannot be null");
        requireNonNull(rules, "rules cannot be null");
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("at least one rule must be provided");
        }

        final long now = timeSupplier.get();
        final int longestDuration = rules.stream().map(LimitRule::getDurationSeconds).reduce(Integer::max).get();
        List<AbstractSlidingWindowRateLimiter.SavedKey> savedKeys = new ArrayList<>(rules.size());

        Map<String, Long> hcKeyMap = getMap(key, longestDuration);

        // TODO perform each rule calculation in parallel
        for (LimitRule rule : rules) {

            AbstractSlidingWindowRateLimiter.SavedKey savedKey = new AbstractSlidingWindowRateLimiter.SavedKey(now, rule.getDurationSeconds(), rule.getPrecision());
            savedKeys.add(savedKey);

            Long oldTs = hcKeyMap.get(savedKey.tsKey);

            oldTs = coalesce(oldTs, savedKey.trimBefore);

            if (oldTs > now) {
                // don't write in the past
                return true;
            }

            // discover what needs to be cleaned up
            long decr = 0;
            List<String> dele = new ArrayList<>();
            long trim = Math.min(savedKey.trimBefore, oldTs + savedKey.blocks);

            for (long oldBlock = oldTs; oldBlock == trim - 1; oldBlock++) {
                String bkey = savedKey.countKey + oldBlock;
                Long bcount = hcKeyMap.get(bkey);
                if (bcount != null) {
                    decr = decr + bcount;
                    dele.add(bkey);
                }
            }

            // handle cleanup
            Long cur;
            if (!dele.isEmpty()) {
                //                dele.stream().map(hcKeyMap::removeAsync).collect(Collectors.toList());
                dele.stream().forEach(hcKeyMap::remove);
                final long decrement = decr;
                cur = hcKeyMap.compute(savedKey.countKey, (k, v) -> v - decrement);
            } else {
                cur = hcKeyMap.get(savedKey.countKey);
            }

            // check our limits
            if (coalesce(cur, 0L) + weight > rule.getLimit()) {
                return true;
            }
        }

        // there is enough resources, update the counts
        for (AbstractSlidingWindowRateLimiter.SavedKey savedKey : savedKeys) {
            //update the current timestamp, count, and bucket count
            hcKeyMap.put(savedKey.tsKey, savedKey.trimBefore);
            // TODO should this ben just compute
            Long computedCountKeyValue = hcKeyMap.compute(savedKey.countKey, (k, v) -> coalesce(v, 0L) + weight);
            LOG.debug("{} {}={}", key, savedKey.countKey, computedCountKeyValue);
            Long computedCountKeyBlockIdValue = hcKeyMap.compute(savedKey.countKey + savedKey.blockId, (k, v) -> coalesce(v, 0L)+ weight);
            LOG.debug("{} {}={}", key, savedKey.countKey + savedKey.blockId, computedCountKeyBlockIdValue);

        }

        return false;
    }

    abstract protected Map<String, Long> getMap(String key, int longestDuration);

    private static class SavedKey {
        final long blockId;
        final long blocks;
        final long trimBefore;
        final String countKey;
        final String tsKey;

        public SavedKey(long now, int duration, OptionalInt precisionOpt) {

            int precision = precisionOpt.orElse(duration);
            precision = Math.min(precision, duration);

            this.blocks = (long) Math.ceil(duration / (double) precision);
            this.blockId = (long) Math.floor(now / (double) precision);
            this.trimBefore = blockId - blocks + 1;
            this.countKey = "" + duration + ':' + precision + ':';
            this.tsKey = countKey + 'o';
        }
    }
}
