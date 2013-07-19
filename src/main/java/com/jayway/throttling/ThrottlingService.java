package com.jayway.throttling;

import java.util.concurrent.Callable;

/**
 * Responsible for keeping track of the number of credits left for each customer
 * within a time interval.
 */
public interface ThrottlingService {
    /**
     * Decrease credits and check if the account is throttled.
     * 
     * @param account
     *            the unique id of the customer account
     * @param cost
     *            the cost of this particular call
     * @param newInterval
     *            callback to get the initial settings for an interval. In a
     *            real application it might take some time to get this value and
     *            therefore we should not do this on every request.
     * @return true if the request should be allowed, false if the account
     *         should be throttled
     */
    boolean allow(String account, long cost, Callable<Interval> newInterval);
}
