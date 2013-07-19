package com.jayway.throttling;

public class Interval {
    public final int seconds;
    public final long credits;

    public Interval(int seconds, long credits) {
        this.seconds = seconds;
        this.credits = credits;
    }
}
