package com.jayway.throttling.app;

import static spark.Spark.get;
import static spark.Spark.post;

import java.util.concurrent.Callable;

import javax.servlet.http.HttpServletResponse;

import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;

import com.jayway.throttling.Interval;
import com.jayway.throttling.ThrottlingContext;
import com.jayway.throttling.ThrottlingService;

/**
 * Responsible for exposing an HTTP API so that you can test your
 * implementation.
 */
public class StartWebServer {
    public static void main(String[] args) throws Exception {
        start(getFactoryClass(args).newInstance());
    }

    private static Class<? extends ThrottlingContext> getFactoryClass(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("You must enter the classname of the ThrottlingContext!");
            System.exit(0);
        }
        return Class.forName(args[0]).asSubclass(ThrottlingContext.class);
    }

    public static void start(ThrottlingContext context) {
        Spark.setPort(8080);
        final ThrottlingService throttlingService = context.getThrottlingService();
        get(new Route("/hello") {
            @Override
            public Object handle(Request request, Response response) {
                String name = request.queryParams("name");
                return "Hello " + name;
            }
        });
        post(new Route("/accounts/:account/demo") {
            @Override
            public Object handle(Request request, Response response) {
                String account = request.params("account");
                boolean allow = throttlingService.allow(account, 1, constant(new Interval(60, 10)));
                if (allow) {
                    return "OK";
                } else {
                    response.status(HttpServletResponse.SC_PAYMENT_REQUIRED);
                    return "THROTTLED";
                }
            }
        });
        post(new Route("/single") {
            @Override
            public Object handle(Request request, Response response) {
                String account = request.queryParams("account");
                boolean allow = new ThrottledRequest(throttlingService, request).perform(account);
                if (allow) {
                    return "OK";
                } else {
                    response.status(HttpServletResponse.SC_PAYMENT_REQUIRED);
                    return "THROTTLED";
                }
            }
        });
        post(new Route("/multi") {
            @Override
            public Object handle(Request request, Response response) {
                ThrottledRequest r = new ThrottledRequest(throttlingService, request);
                int calls = parseInt(request, "calls");
                int accounts = parseInt(request, "accounts");
                int throttledCount = 0;
                long startTime = System.currentTimeMillis();
                for (int indx = 0; indx < calls; indx++) {
                    String randomAccount = "a" + (int) (Math.random() * accounts);
                    if (!r.perform(randomAccount)) {
                        throttledCount++;
                    }
                }
                response.type("application/json");
                long time = System.currentTimeMillis() - startTime;
                return String.format("{\"calls\": %s, \"time\": %s, \"throttled\": %s}", calls, time, throttledCount);
            }
        });
    }

    static class ThrottledRequest {
        private final long cost;
        private final Callable<Interval> newInterval;
        private final ThrottlingService throttlingService;

        public ThrottledRequest(ThrottlingService throttlingService, Request request) {
            this.throttlingService = throttlingService;
            this.cost = parseInt(request, "cost");
            final int intervalInSeconds = parseInt(request, "intervalInSeconds");
            final long creditsPerIntervalValue = parseInt(request, "creditsPerInterval");
            this.newInterval = constant(new Interval(intervalInSeconds, creditsPerIntervalValue));
        }

        public boolean perform(String account) {
            return throttlingService.allow(account, cost, newInterval);
        }
    }

    private static int parseInt(Request request, String name) {
        try {
            return Integer.parseInt(request.queryParams(name));
        } catch (NumberFormatException e) {
            throw new NumberFormatException(name + "=" + request.queryParams(name) + " is not a number");
        }
    }

    private static <T> Callable<T> constant(final T value) {
        return new Callable<T>() {
            @Override
            public T call() throws Exception {
                return value;
            }
        };
    }
}
