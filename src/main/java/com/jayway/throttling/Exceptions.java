package com.jayway.throttling;

public class Exceptions {
    public static RuntimeException asUnchecked(Throwable e) {
        if (e == null)
            throw new NullPointerException("Missing exception");
        Exceptions.<RuntimeException> sneakyThrow0(e);
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow0(Throwable t) throws T {
        throw (T) t;
    }
}
