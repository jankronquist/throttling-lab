This is a lab to explore various clustering solutions, mostly key/value stores. 
To do something interesting we implement throttling.

Please note that this is not intended as a production grade throttling library. 
The primary purpose is to explore and give an understanding of clustered state.
Therefore, the design and implementation mostly favours simplicity before anything else.

Get started
-----------
To get started with a throttling implementation using [spymemcached](https://code.google.com/p/spymemcached/)
with an embedded [jmemcached](https://code.google.com/p/jmemcache-daemon/):

	git clone https://github.com/jankronquist/throttling-lab.git
	cd throttling-lab
	mvn package
	java -jar target/throttling-1.0-SNAPSHOT.one-jar.jar com.jayway.throttling.impl.memcached.Memcached
	curl -X POST http://localhost:8080/accounts/smallCustomer/demo

(Make sure you have Java 7 and Maven 3 installed)

Problem description
-------------------
When you publish an API you sometimes need to limit the number of calls each customer can make to either 
reduce the load on your servers or because each call actually involves some cost to you.  In this lab
we use a simple algorithm: Within a time interval each customer (account) gets a number of credits. 
Each call consumes credits (possibly different amount per request). 
When all credits have been consumed no more calls are allowed within the time interval.

Your job is to implement this throttling mechanism by implementing this interface:

	public interface ThrottlingService {
		boolean allow(String account, long cost, Callable<Interval> newInterval);
	}
	
	public class Interval {
		public final int seconds;
		public final long credits;

		public Interval(int seconds, long credits) {
			this.seconds = seconds;
			this.credits = credits;
		}
	}

You need to consider the following things:

* Web server load balancing - Multiple web servers should be able to use the same data
* Failover - What happens when a server holding the data crashes? Do we replicate?
* Scalability - We should be able to handle both many customers and many simultaneous requests

The lab has the following steps:

* Implement a solution given a key/value store
* Deploy on Amazon
* Test (load, failover)

To get you started we have provided an implementation using [memcached](http://memcached.org/), see below. 
Notice that you can use [Amazon ElastiCache](http://aws.amazon.com/elasticache/) or in fact any implementation
supporting the protocol! 

Suggested key/value stores to investigate:

* [redis](http://redis.io/)
* [cassandra](http://cassandra.apache.org/) (implements memcached!)
* [Couchbase](http://www.couchbase.com/) (implements memcached!)
* [Ehcache](http://ehcache.org/)
* [Amazon DynamoDB](http://aws.amazon.com/dynamodb/)
* [JGroups](http://www.jgroups.org/)
* [ZooKeeper](http://zookeeper.apache.org/)
* [Hazelcast](http://www.hazelcast.com/) (notice that `decr` is [not supported in memcached protocol](https://github.com/hazelcast/hazelcast/issues/210))

You could also investigate third party services such as:

* [Redis cloud](http://redis-cloud.com/)
* [Memcached cloud](http://garantiadata.com/memcached)
* [instaclustr](https://www.instaclustr.com/) (cassandra as a service)

Other suggestions are welcome!

Memcached implementation
------------------------
The memcached implementation uses a cached value that is decreased using `decr` until it reaches 0, then throttles all requests until the value is expired from the cache. Since standard memcached does not support sharding the clients have to shard the data. This must be done consistently, ie all clients must store a specific key at a certain server. To solve this spymemcached implements the [Ketama consistent hashing](http://www.audioscrobbler.net/development/ketama/) algorithm.

To get you started without having to install anything, an embedded jmemcached daemon is started if you don't specify any external servers.

Command line arguments:

* -Djmemcached.enabled - controls if jmemcached should be used or not (enabled by default if no servers specified)
* -Djmemcached.port - port number to use for embedded jmemcached (default 11222, to avoid collision with install server)
* -Djmemcached.maximumCapacity - number of objects to keep in the cache (default 1000)
* -Dmemcached.servers - commaseparated list of server:port or nothing to use the embedded jmemcached 

For example connecting to 2 external memcached servers:

	java -jar target/throttling-1.0-SNAPSHOT.one-jar.jar -Dmemcached.servers="someserver.com:11211,another.com:11211" com.jayway.throttling.impl.memcached.Memcached


Things to investigate:

* What happens when a memcached server goes down? When it comes back up?
* ...

HTTP API
--------
The HTTP API has been greatly simplified and is only intended for testing and not how to actually use a ThrottlingService in a real application:

* No authentication or other security have been implemented
* No actual business logic have been implemented

To allow us to focus on the implementation and performance of the ThrottlingService the API exposes only the ThrottlingService itself. The following resources exists:

* `/accounts/:account/demo` a single call with `cost=1`, `intervalInSeconds=60` and `creditsPerInterval=10`. This is a demo of how it could look like in a real application. Still, no actual business logic is performed.
* `/single` which performs a single call to allow with configurable cost, intervalInSeconds and creditsPerInterval. This is useful to perform tests with a single account and specific settings.
* `/multi` which performs a number of calls to random accounts. This is useful if you want to test load, and only focus on the throttling implementation and not the HTTP call and its latency. The accounts used are "a" followed by a number, for example "a17". Use the `accounts` parameter to specify how many accounts you want to randomize between.

The arguments should be self explanatory using these curls:

	curl -X POST http://localhost:8080/accounts/smallCustomer/demo
	curl -X POST -d "account=smallCustomer" -d "cost=1" -d "intervalInSeconds=60" -d "creditsPerInterval=5" http://localhost:8080/single
	curl -X POST -d "accounts=100" -d "calls=100" -d "cost=1" -d "intervalInSeconds=60" -d "creditsPerInterval=10" http://localhost:8080/multi

Notice how you can specify the length and credits of the interval. The reason for doing to is that is makes configuring the application really simple. In fact, no configuration is required and everything can be controlled from your test case! Obviously, this is not how you would configure a real application! 

However, these values are only used when starting a new interval. For example trying to change the `creditsPerInterval` will not take effect until the first interval has timed out: 

	curl -X POST -d "account=smallCustomer" -d "cost=1" -d "intervalInSeconds=60" -d "creditsPerInterval=1" http://localhost:8080/single
	; OK

	curl -X POST -d "account=smallCustomer" -d "cost=1" -d "intervalInSeconds=60" -d "creditsPerInterval=1" http://localhost:8080/single
	; THROTTLED
	
	curl -X POST -d "account=smallCustomer" -d "cost=1" -d "intervalInSeconds=60" -d "creditsPerInterval=10" http://localhost:8080/single
	; still THROTTLED

The return value of `/multi` is a JSON like `{"calls": 100, "time": 55, "throttled": 3}`. This is informational only and can be useful to verify that your test results are making sense. `time` is the time in milliseconds it took to perform all ThrottlingService calls. `throttled` is how many of those that were throttled. 

The lab
-------

### Implementing a ThrottlingService ###

Create one class that implements `ThrottlingService` and another that implements `ThrottlingContext` which is used to initialize and configure your implementation. 

Make sure your implementation works locally! 

### Deploy on Amazon ###

Launching an instance:

1. Login, select EC2 instances and make sure to select an appropriate region
2. Launch an instance, "Ubuntu Server 12.04.2 LTS"
3. Select instance type
4. No "Advanced Instance Options"
5. No "Storage Device Configuration"
6. Specify instance name 
7. Create a new keypair (or use an existing if you have one!)
8. Use an existing security group with SSH access ("Lab")
9. Launch!

If you created a new keypair:

1. Move the `pem` file to `~/.ssh/lab.pem`
2. `chmod 600 ~/.ssh/lab.pem`

Connecting to your instance:

	ssh -i ~/.ssh/lab.pem ubuntu@<hostname>

Preparing the instance:

	sudo apt-get update
	sudo apt-get install openjdk-7-jre-headless

Build, deploy and run:

	mvn package
	scp -i ~/.ssh/lab.pem target/throttling-1.0-SNAPSHOT.one-jar.jar ubuntu@<hostname>:
	ssh -i ~/.ssh/lab.pem ubuntu@<hostname> "java -jar throttling-1.0-SNAPSHOT.one-jar.jar com.jayway.throttling.impl.memcached.Memcached"

You are of course free to create scripts, change pom.xml to handle automatic deployment, use ssh config or whatever you feel is convenient for you.

### Running JMeter ###

Generating load using JMeter is easy. However, it may be useful to run the tests on the same local network as
your server is running on, to avoid introducing latency. The following process can both be run locally and 
on a separate Amazon EC2 instance. It generates load by calling `multi` as specified above.

Download and unzip JMeter to $JMETER_HOME.  `cd jmeter` in the root of this project. Run load test using the following command:

	$JMETER_HOME/bin/jmeter -n -l results.jtl -t load.jmx -p jmeter.properties

This does the following:

* `-n` means non-gui
* `-l results.jtl` means write result to results.jtl (this can later be loaded and visualized using JMeter UI)
* `-t load.jmx` means run the tests specified in the file load.jmx
* `-p jmeter.properties` configures jmeter using the provided properties (simply to enable summaries on the command line)

The following parameters can be used to configure the load. 

* `-Dhost=localhost` is the hostname for the web server
* `-Dport=8080` is the port for the web server
* `-Dthreads=40` is the number of threads jmeter will use
* `-DloopCount=1000` is the number of calls to `multi` each thread will perform
* `-DcallsPerMulti=100` is the number of calls to the ThrottlingService that will be done per request
* `-Daccounts=1000` is the number of random accounts used for calling multi.
* `-DintervalInSeconds=60` is the TTL for a counter
* `-DcreditsPerInterval=100` is the number of credits an account have during an interval
* `-Dcost=1` is the cost of a call

Notice that the total number of calls to ThrottlingService is `threads * loopCount * callsPerMulti`, that is 4.000.000 calls with the default settings. My developer machine handles around 30-40.000 calls per second, which means 300-400 requests to `multi` with the default 100 calls each.

### Failover test ###

Since the default memcached implementation has an embedded jmemcached we can easily set up a cluster that shares data:

	ssh -i ~/.ssh/lab.pem ubuntu@<server1> "java -jar throttling-1.0-SNAPSHOT.one-jar.jar -Dmemcached.servers="server1.com:11211,server2:11211" -Djmemcached.enabled=true com.jayway.throttling.impl.memcached.Memcached"
	ssh -i ~/.ssh/lab.pem ubuntu@<server2> "java -jar throttling-1.0-SNAPSHOT.one-jar.jar -Dmemcached.servers="server1.com:11211,server2:11211" -Djmemcached.enabled=true com.jayway.throttling.impl.memcached.Memcached"

Now you can create some accounts and set the value of the counter, for example by initializing it very low and make sure it has been throttled. Kill one of the webservers. Make some calls and see if every account is still throttled. What is going on?

You can also perform a similar test while creating load using jmeter. What happens when you shut down one of the webservers? Make sure your load is generated to other one!

Perform similar investigations for your own implementation, ie without the embedded jmemcached. 
