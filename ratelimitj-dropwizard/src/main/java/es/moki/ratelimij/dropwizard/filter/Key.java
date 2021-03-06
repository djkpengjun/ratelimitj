package es.moki.ratelimij.dropwizard.filter;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ResourceInfo;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Objects.isNull;

public enum Key implements KeyProvider {

    DEFAULT {
        @Override
        public String create(final HttpServletRequest request,
                             final ResourceInfo resource) {
            return "dw-ratelimit:" + requestKey(request) + ":" + resourceKey(resource);
        }

        private String requestKey(final HttpServletRequest request) {
            return selectOptional(
                    () -> userRequestKey(request),
                    () -> xForwardedForRequestKey(request),
                    () -> ipRequestKey(request))
                    .orElse("NO_REQUEST_KEY");
        }
    };

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private static String resourceKey(final ResourceInfo resource) {
        return resource.getResourceClass().getTypeName()
                + "#" + resource.getResourceMethod().getName();
    }

//    private static Optional<String> requestKey(final HttpServletRequest request) {
//        if (nonNull(request.getRemoteUser())) {
//            return Optional.of("user#" + request.getRemoteUser());
//        } else if (nonNull(request.getHeader(HEADER))) {
//            return Optional.of("xfwd4#" + request.getHeader(HEADER));
//        } else if (nonNull(request.getRemoteAddr())) {
//            return Optional.of("ip#" + request.getRemoteAddr());
//        }
//        return Optional.empty();
//    }

    static Optional<String> userRequestKey(HttpServletRequest request) {
        String remoteUser = request.getRemoteUser();
        if (isNull(remoteUser)) {
            return Optional.empty();
        }
        return Optional.of("user#" + remoteUser);
    }

    static Optional<String> xForwardedForRequestKey(HttpServletRequest request) {

        String header = request.getHeader(X_FORWARDED_FOR);
        if (isNull(header)) {
            return Optional.empty();
        }

        Optional<String> originatingClientIp = Stream.of(header.split(",")).findFirst();
        return originatingClientIp.map(ip -> "xfwd4#" + ip);
    }

    static Optional<String> ipRequestKey(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (isNull(remoteAddr)) {
            return Optional.empty();
        }
        return Optional.of("ip#" + remoteAddr);
    }

    @SafeVarargs
    static <T> Optional<T> selectOptional(Supplier<Optional<T>>... optionals) {
        return Arrays.stream(optionals)
                .reduce((s1, s2) -> () -> s1.get().map(Optional::of).orElseGet(s2))
                .orElse(Optional::empty).get();
    }
}
