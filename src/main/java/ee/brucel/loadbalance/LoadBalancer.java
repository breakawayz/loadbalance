package ee.brucel.loadbalance;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

public class LoadBalancer {
	private static final Logger logger = Logger.getLogger(LoadBalancer.class);
	private static Map<String, SortedSet<LoadBalancee>> balanceeGroups = new ConcurrentHashMap<String, SortedSet<LoadBalancee>>();
	private static Map<String, HealthContainer> unhealthyURIs = new ConcurrentHashMap<String, HealthContainer>();
	private static Map<String, URI[]> sortedURIs = new ConcurrentHashMap<String, URI[]>();

	public static <T> T doWorkOnBestBalancee(String key, ResourceWorkStrategy<T> strategy, Map<String, Object> parameters){
		URI bestResource = getBestResource(key);
		boolean workPerformed = false;
		T returnValue = null;
		int attemptCount = 0;
		while (!workPerformed){
			boolean unhealthy = false;
			try{
				returnValue = strategy.processWork(bestResource, parameters);
			}catch(ResourceUnhealthyException rue){
				unhealthy = true;
			}finally{
				returnResource(key, bestResource);
			}
			if (unhealthy){
				reportUnhealthy(key, bestResource);
				continue;
			}else{
				workPerformed = true;
			}
			bestResource = getBestResource(key);
		}
		return returnValue;
	}

	public static <T> T doWorkOnStickyBalancee(String key, 
		ResourceWorkStrategy<T> strategy, Map<String, Object> parameters,
		StickySessionStrategy stickyStrategy, String stickySessionIdentifier) throws ResourceUnhealthyException {
		URI stickyResource = getStickyURI(key, stickySessionIdentifier,
			stickyStrategy);
		T returnValue = null;
		try{
			returnValue = strategy.processWork(stickyResource, parameters);
		}catch(ResourceUnhealthyException rue){
			logger.info(rue);
			reportUnhealthy(key, stickyResource);
			throw rue;
		}finally{
			returnResource(key, stickyResource);
		}
		return returnValue;
	}

	protected static URI getBestResource(String key) {
		SortedSet<LoadBalancee> balancees = balanceeGroups.get(key);
		if (balancees == null) {
			return null;
		}
		URI resource = null;
		synchronized (balancees) {
			HealthContainer unhealthyInThisGroup = unhealthyURIs.get(key);
			if (unhealthyInThisGroup == null) {
				unhealthyInThisGroup = new HealthContainer();
				unhealthyURIs.put(key, unhealthyInThisGroup);
			}
			if (balancees.last().availablePermits() == 0) {
				// need to add to permits.
				addPermits(key);
			}
			LoadBalancee candidate = null;
			for (LoadBalancee balancee : balancees) {
				if (unhealthyInThisGroup.isURIUnhealthy(balancee.getResource())) {
					continue;
				} else {
					candidate = balancee;
					break;
				}
			}

			if (candidate == null) {
				// if all are unhealthy, then let's return the one which was
				// least burdened. Consumer should handle that all servers.
				// are down.
				candidate = balancees.first();
			}
			balancees.remove(candidate);
			resource = candidate.acquirePermit();
			balancees.add(candidate);
		}
		return resource;

	}

	//Returns a URI based on the balancee group, the sticky session identifier,
	//and a strategy that ostensibly selects a URI in a consistent manner given
	//the other arguments.
	//Note: This does not do any checks against the health. There is an 
	//assumption that the consumer would rather have a URI that does not work
	//rather than non-sticky [inconsistent] behavior based on server health.
	protected static URI getStickyURI(String key, String stickySessionIdentifier,
			StickySessionStrategy strategy) {
		URI uri = strategy.giveURIByStrategy(stickySessionIdentifier,
				sortedURIs.get(key));
		SortedSet<LoadBalancee> balancees = balanceeGroups.get(key);
		if (balancees == null) {
			return null;
		}
		synchronized (balancees) {
			boolean requiresPermits = false;
			LoadBalancee stuckServer = null;
			for (LoadBalancee balancee : balancees) {
				if (balancee.getResource().equals(uri)) {
					stuckServer = balancee;
					if (balancee.availablePermits() == 0) {
						requiresPermits = true;
					}
					break;
				}
			}
			if (requiresPermits) {
				addPermits(key);
			}
			balancees.remove(stuckServer);
			stuckServer.acquirePermit();
			balancees.add(stuckServer);
		}
		return uri;
	}

	protected static void reportUnhealthy(String key, URI resource) {
		SortedSet<LoadBalancee> balancees = balanceeGroups.get(key);
		if (balancees == null) {
			return;
		}
		logger.warn(resource + " marked as unhealthy by LoadBalancer consumer");
		synchronized (balancees) {
			for (LoadBalancee balancee : balancees) {
				if (balancee.getResource().equals(resource)) {
					HealthContainer unhealthyInThisGroup = unhealthyURIs
							.get(key);
					if (unhealthyInThisGroup == null) {
						unhealthyInThisGroup = new HealthContainer();
						unhealthyURIs.put(key, unhealthyInThisGroup);
					}
					unhealthyInThisGroup.markAsUnhealthy(resource);
					break;
				}
			}
		}
	}

	protected static void returnResource(String key, URI resource) {
		SortedSet<LoadBalancee> balancees = balanceeGroups.get(key);
		if (balancees == null) {
			return;
		}
		synchronized (balancees) {
			for (LoadBalancee balancee : balancees) {
				if (balancee.getResource().equals(resource)) {
					// need to remove and add so that the sorting of the
					// SortedSet is preserved.
					balancees.remove(balancee);
					balancee.releasePermit();
					balancees.add(balancee);
					break;
				}
			}
		}
	}

	public static void initializeGroup(List<URI> initialItems, String key) {
		SortedSet<LoadBalancee> loadBalancees = new TreeSet<LoadBalancee>();
		for (URI uri : initialItems) {
			LoadBalancee balancee = new LoadBalancee(uri);
			loadBalancees.add(balancee);
		}
		balanceeGroups.put(key, loadBalancees);
		URI[] sortedURIsValue = new URI[loadBalancees.size()];
		// Take advantage of the fact that loadBalancees should be sorted
		// alphabetically at this point
		int i = 0;
		for (LoadBalancee balancee : loadBalancees) {
			sortedURIsValue[i++] = balancee.getResource();
		}
		sortedURIs.put(key, sortedURIsValue);
		//Clear unhealthy URIs
		unhealthyURIs.remove(key);
	}

	private static void addPermits(String key) {

		SortedSet<LoadBalancee> balancees = balanceeGroups.get(key);
		synchronized (balancees) {
			// need to add to all. Also need to warn that
			// there is likely a consumer that is 'leaking'
			// balancees.
			logger.warn("A consumer is likely leaking balancees for key " + key
					+ ". Re-adding to permits.");
			for (LoadBalancee balancee : balancees) {
				// Add 1000 permits back to each semaphore.
				// Fairness is still preserved as each balancee
				// is consistently added to.
				balancee.addBackPermits();
			}
		}
	}

	static void setupTest(List<URI> initialItems, String key) {
		balanceeGroups = new ConcurrentHashMap<String, SortedSet<LoadBalancee>>();
		initializeGroup(initialItems, key);
	}
}
