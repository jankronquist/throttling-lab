package com.jayway.throttling.impl.memcached;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.spy.memcached.AddrUtil;
import net.spy.memcached.KetamaConnectionFactory;
import net.spy.memcached.MemcachedClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jayway.throttling.Exceptions;
import com.jayway.throttling.ThrottlingService;
import com.jayway.throttling.ThrottlingContext;
import com.jayway.throttling.app.StartWebServer;
import com.thimbleware.jmemcached.CacheImpl;
import com.thimbleware.jmemcached.Key;
import com.thimbleware.jmemcached.LocalCacheElement;
import com.thimbleware.jmemcached.MemCacheDaemon;
import com.thimbleware.jmemcached.storage.CacheStorage;
import com.thimbleware.jmemcached.storage.hash.ConcurrentLinkedHashMap;

public class Memcached implements ThrottlingContext {
    private static final Logger logger = LoggerFactory.getLogger(Memcached.class);

    public static void main(String[] args) {
        StartWebServer.start(new Memcached());
    }

    static class Config {
        private final List<String> servers;
        private final boolean jmemcachedEnabled;
        private final int jmemcachedPort;
        private final int maximumCapacity;

        public Config(List<String> servers, boolean jmemcachedEnabled, int jmemcachedPort, int maximumCapacity) {
            this.servers = servers;
            this.jmemcachedEnabled = jmemcachedEnabled;
            this.jmemcachedPort = jmemcachedPort;
            this.maximumCapacity = maximumCapacity;
        }
    }

    private final MemcachedThrottlingService throttlingService;
    private final MemcachedClient memcachedClient;
    private final MemCacheDaemon<LocalCacheElement> jmemcached;

    public Memcached() {
        this(getConfigFromProperties());
    }

    private static Config getConfigFromProperties() {
        List<String> servers = commaSeparate(System.getProperty("memcached.servers"));
        boolean hasArguments = !servers.isEmpty();

        boolean jmemcachedEnabled = Boolean.parseBoolean(System.getProperty("jmemcached.enabled", Boolean.toString(!hasArguments)));
        int jmemcachedPort = Integer.parseInt(System.getProperty("jmemcached.port", Integer.toString(11222)));
        int maximumCapacity = Integer.parseInt(System.getProperty("jmemcached.maximumCapacity", Integer.toString(1000)));
        if (!hasArguments) {
            servers = Arrays.asList("localhost:" + jmemcachedPort);
        }
        return new Config(servers, jmemcachedEnabled, jmemcachedPort, maximumCapacity);
    }

    private static List<String> commaSeparate(String string) {
        if (string == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(string.split(","));
    }

    public Memcached(Config config) {
        if (config.jmemcachedEnabled) {
            logger.info("Starting jmemcached at port " + config.jmemcachedPort);
            jmemcached = startJmemcached(config.jmemcachedPort, config.maximumCapacity);
        } else {
            jmemcached = null;
        }
        try {
            this.memcachedClient = new MemcachedClient(new KetamaConnectionFactory(), AddrUtil.getAddresses(config.servers));
        } catch (IOException e) {
            throw Exceptions.asUnchecked(e);
        }
        this.throttlingService = new MemcachedThrottlingService(memcachedClient);
    }

    @Override
    public ThrottlingService getThrottlingService() {
        return throttlingService;
    }

    private MemCacheDaemon<LocalCacheElement> startJmemcached(int port, int maximumCapacity) {
        MemCacheDaemon<LocalCacheElement> jmemcached = new MemCacheDaemon<LocalCacheElement>();
        // XXX: we should probably allow configuration of the max memory as well
        CacheStorage<Key, LocalCacheElement> storage = ConcurrentLinkedHashMap
                .create(ConcurrentLinkedHashMap.EvictionPolicy.LRU, maximumCapacity, maximumCapacity * 100);
        jmemcached.setCache(new CacheImpl(storage));
        jmemcached.setBinary(false);
        jmemcached.setAddr(new InetSocketAddress(port));
        jmemcached.setVerbose(false);
        jmemcached.start();
        return jmemcached;
    }

    @Override
    public void close() {
        memcachedClient.shutdown();
        jmemcached.stop();
    }
}
