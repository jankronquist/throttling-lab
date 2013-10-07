package com.jayway.throttling.impl.memcached;

import java.util.concurrent.Callable;

import net.spy.memcached.MemcachedClient;

import com.jayway.throttling.Exceptions;
import com.jayway.throttling.Interval;
import com.jayway.throttling.ThrottlingService;

public class MemcachedThrottlingService implements ThrottlingService {

    private final MemcachedClient client;

    public MemcachedThrottlingService(MemcachedClient client) {
        this.client = client;
    }

    /**
     * A cached value that is decreased using decr until it reaches 0, then
     * throttles all requests until the value is expired from the cache.
     * 
     * Notice that decr never makes the value go below 0 and that -1 is used to
     * indicate no value.
     */
    @Override
    public boolean allow(String account, long cost, Callable<Interval> newInterval) {
        long result = client.decr(account, cost);
        if (result > 0) {
            return true;
        } else if (result == 0) {
            return false;
        } else {
            Interval interval = getInterval(newInterval);
            client.set(account, interval.seconds, Long.toString(Math.max(0, 1 + interval.credits - cost)));
            if (cost > interval.credits) {
                return false;
            } else {
                return true;
            }
        }
    }

    private Interval getInterval(Callable<Interval> newInterval) {
        try {
            return newInterval.call();
        } catch (Exception e) {
            throw Exceptions.asUnchecked(e);
        }
    }
}
