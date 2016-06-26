package es.moki.ratelimij.dropwizard;


import es.moki.ratelimij.dropwizard.filter.SimpleRateLimitFilter;
import es.moki.ratelimitj.core.api.RateLimiter;
import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class RateLimitBundle<T extends Configuration> implements ConfiguredBundle<T> {

    private final RateLimiter rateLimit;

    public RateLimitBundle(RateLimiter rateLimit) {
        this.rateLimit = rateLimit;
    }

    @Override
    public void run(T configuration, Environment environment) throws Exception {

        // TODO provide decoupled mechanism to bind Ratelimiter implementation to dropwizard

        SimpleRateLimitFilter rateLimitFilter = new SimpleRateLimitFilter(rateLimit);
        environment.jersey().register(rateLimitFilter);
        //environment.jersey().register(DateRequiredFeature.class);
    }

    @Override
    public void initialize(Bootstrap<?> bootstrap) {

    }
}
