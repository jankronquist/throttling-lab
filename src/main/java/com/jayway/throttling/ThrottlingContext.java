package com.jayway.throttling;

/**
 * Responsible for initializing, holding shutting down all resources necessary
 * for the ThrottlingService. In a real application, this would perhaps be part
 * of a Spring context or similar.
 * 
 * This is useful if the ThrottlingService embeds other resources.
 */
public interface ThrottlingContext {
    ThrottlingService getThrottlingService();

    void close();
}
