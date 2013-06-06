loadbalance
===========

I've made a very simple software 'load balancer' in Java for fun. It does not attempt to actually connect to the resources that it balances, but instead serves out a URI to the consumer and subsequently relies on the consumer to perform whatever operation it needs to and return the URI to the load balancer [preferably in a try/finally block].

Features
========
- A concept of 'fairness'. If a balancee has more open requests out to it than its siblings, its siblings will be considered for balancing prior to that balancee. See testFairnessWhenBalanceesTakeLongTime for a 'story' example.
- Ability for consumers to report health (and for unhealthy resources to be removed from load balancing consideration). See testHealthReporting for an example of usage.
- Operates under multithreaded execution. See testMultiThreadedBehaviorIsFair which shows this.
- Supports sticky sessions (based off of a String representation of a sticky session ID), with the ability for the consumer to define their own strategy of how that sticky session ID maps to a given array of URIs. Examples of usage are provided in the unit tests testShowStringHashingStickyStrategy and testShowAnotherStickyStrategy.
- Supports multiple groups of load balancees, segmented by a String representation of a key. An example of what this means is provided in the unit test testMultipleKeyDoesNotCauseConflict.

Building
========
Consumers need to have a Maven setup on their local. Afterwards, a 'mvn clean install' will generally put loadbalance-<version number>.jar into their local Maven repository assuming a successful build.

Usage
=====
Consumers need to first populate the LoadBalancer with URIs before usage. This can be accomplished by passing a List<URI> of desired balancees, along with a String key, to LoadBalancer.initializeGroup as so:
LoadBalancer.initializeGroup(balancees, key);

The key argument would need to be used with any subsequent request to the LoadBalancer in order to get the correct group of balancees.

Subsequently, consumers are then able to ask for URIs which will be delivered fairly. Consumers should return the resource once done to ensure fairness across the balancees.
URI resource = LoadBalancer.getBestResource(key);
//Operate on the URI somehow
LoadBalancer.returnResource(resource);

If, while operating on the URI, the consumer found that the URI pointed at an unavailable resource, the consumer could report that URI as unhealthy as below. It is good practice to return the URI as well, to ensure that fairness will resume when the resource becomes available again.
LoadBalancer.reportUnhealthy(resource);
LoadBalancer.returnResource(resource);

If sticky sessions are needed, the facility is provided to the consumer to give a strategy to return a URI based on that strategy, provided with a key. The signature is below.

LoadBalancer.getStickyURI(String key, String stickySessionIdentifier, StickySessionStrategy strategy)

It is important to note that the sticky URI retrieval also participates in the fairness. That is to say, if the consumer desires to make certain requests sticky and others not, the balancees returned by the load balancer will still be fair as according to the number of outstanding requests per load balancee.
