loadbalance
===========

I've made a very simple software 'load balancer' in Java for fun. The consumer is to specify ResourceWorkStrategy&lt;ReturnType&gt; implementations to use, which will perform the work on the URI.

Features
========
- A concept of 'fairness'. If a balancee has more open requests out to it than its siblings, its siblings will be considered for balancing prior to that balancee. See testFairnessWhenBalanceesTakeLongTime for a 'story' example.
- Ability for consumers to report health (and for unhealthy resources to be removed from load balancing consideration). See testHealthReporting for an example of usage.
- Operates under multithreaded execution. See testMultiThreadedBehaviorIsFair which shows this.
- Supports sticky sessions (based off of a String representation of a sticky session ID), with the ability for the consumer to define their own strategy of how that sticky session ID maps to a given array of URIs. Examples of usage are provided in the unit tests testShowStringHashingStickyStrategy and testShowAnotherStickyStrategy.
- Supports multiple groups of load balancees, segmented by a String representation of a key. An example of what this means is provided in the unit test testMultipleKeyDoesNotCauseConflict.
- Strategy-based usage of resources. Does not limit consumers to simple web concepts, but would allow for flexible activities based on what the consumer's needs are.

Building
========
Consumers need to have a Maven setup on their local. Afterwards, a 'mvn clean install' will generally put loadbalance-&lt;version number&gt;.jar into their local Maven repository assuming a successful build.

Usage
=====
Consumers need to first populate the LoadBalancer with URIs before usage. This can be accomplished by passing a List&lt;URI&gt; of desired balancees, along with a String key, to LoadBalancer.initializeGroup as so:
````
LoadBalancer.initializeGroup(balancees, key);
````
The key argument would need to be used with any subsequent request to the LoadBalancer in order to get the correct group of balancees.

If no need for sticky sessions exist, the following signature is to be used. This will perform the work listed in the ResourceWorkStrategy implementation, upon the URI that is currently least burdened.
````
public static <T> T doWorkOnBestBalancee(String key, ResourceWorkStrategy<T> strategy, Map<String, Object> parameters)
````
If sticky sessions are desired, a similar signature allows this, and adds in the need for a StickySessionStrategy as well as a 'session key' to be potentially used within the StickySessionStrategy.
````
public static <T> T doWorkOnStickyBalancee(String key, ResourceWorkStrategy<T> strategy, 
	Map<String, Object> parameters, StickySessionStrategy stickyStrategy, String stickySessionIdentifier) 
throws ResourceUnhealthyException
````

It is important to note that the sticky URI retrieval also participates in the fairness. That is to say, if the consumer desires to make certain requests sticky and others not, the balancees returned by the load balancer will still be fair as according to the number of outstanding requests per load balancee.

Implementing ResourceWorkStrategy
=================================
ResourceWorkStrategy is expected to be implementing with a specific return type in mind. Thus, a ResourceWorkStrategy&lt;String&gt; implementation would return a String as a result of the work done.

The class definition for such an implementation might look like:
````
public class StringReturningWorkStrategy implements ResourceWorkStrategy<String> {
	public String processWork(URI uri, Map<String, Object> parameters) 
		throws ResourceUnhealthyException, IllegalArgumentException {
		... some implementation ...
	}
}
````

A "Hello World" [literally] implementation might include the below, for a ResourceWorkStrategy&lt;String&gt;
````
public String processWork(URI uri, Map<String, Object> parameters) throws ResourceUnhealthyException, IllegalArgumentException {
	return "Hello World";
}
````

Something more interesting, which relies on the URIs in the loadbalancer population being HTTP URIs, might look like the below. This function would attempt to HTTP-GET the item in the sub-path listed by the consumer-supplied parameter PATH and return the body of the HTTP response as a String. Note that the implementation throws both IllegalArgumentException (if something in the parameters Map is of issue) as well as ResourceUnhealthyException (when the implementation believes that the passed in resource is unhealthy for whatever reason).
````
public String processWork(URI uri, Map<String, Object> parameters) throws ResourceUnhealthyException, IllegalArgumentException {
	//Pulled and modified from: http://stackoverflow.com/a/238634/1768374
	URL url;
	InputStream is = null;
	BufferedReader br;
	String line;
	StringBuilder sb = new StringBuilder();
	String attemptURL = "";
	try {
		attemptURL = uri.toASCIIString() + (String)parameters.get("PATH");
		url = new URL(attemptURL);
		is = url.openStream();  // throws an IOException
		br = new BufferedReader(new InputStreamReader(is));
		while ((line = br.readLine()) != null) {
			sb.append(line);
		}
	} catch (MalformedURLException mue) {
		throw new IllegalArgumentException(attemptURL);
	} catch (IOException ioe) {
		throw new ResourceUnhealthyException("There was an issue getting data from " + attemptURL, ioe);
	} finally {
		try {
			is.close();
		} catch (IOException ioe) {
			// nothing to see here
		}
	}
	return sb.toString();
}
````

Implementing StickySessionStrategy
==================================
The StickySessionStrategy interface requires consumers to implement the following function:
````
URI giveURIByStrategy(String stickySessionKey, URI[] uris);
````

An example implementation below, which would stickily distribute load across the set of uris, might be the below:
````
public URI giveURIByStrategy(String stickySessionKey, URI[] uris){
  return uris[Math.abs(stickySessionKey.hashCode()) % uris.length];
}
````
Whether the load is distributed fairly is then based entirely on whether the strings used have hashCodes which are fairly distributed.

If the key had some information about which URI to pick (i.e., was a JSESSIONID with the desired hostname appended to the end), the below would choose it appropriately.
````
public URI giveURIByStrategy(String stickySessionKey, URI[] uris) {
  for (URI uri : uris){
    if (stickySessionKey.contains(uri.getHost())){
      return uri;
    }
  }
}
````

The consumer is free to implement any strategy, but is advised to ensure that the implementation will genuinely give true stickiness.

Internal API
============
Consumers will generally not be able to call into these functions. However, their behavior is listed here for inquisitive minds.

The following function allows the internals to ask for URIs which will be delivered fairly. The internal API should return the resource once done to ensure fairness across the balancees.
````
URI resource = LoadBalancer.getBestResource(key);
//Operate on the URI somehow
LoadBalancer.returnResource(resource);
````
If, while operating on the URI, the internal API found that the URI pointed at an unavailable resource, the API should report that URI as unhealthy as below. It is good practice to return the URI as well, to ensure that fairness will resume when the resource becomes available again.
````
LoadBalancer.reportUnhealthy(resource);
LoadBalancer.returnResource(resource);
````
If sticky sessions are needed, the facility is provided to the consumer to give a strategy to return a URI based on that strategy, provided with a key. The signature that the internal API uses is below.
````
LoadBalancer.getStickyURI(String key, String stickySessionIdentifier, StickySessionStrategy strategy)
````
