package ee.brucel.loadbalance;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Assert;

import org.junit.Assume;
import org.junit.Test;

import ee.brucel.loadbalance.LoadBalancer;
import ee.brucel.loadbalance.StickySessionStrategy;

public class LoadBalancerTest {
	// Load balancer will allow several different resource 'groups'
	// to be balanced, balanced off of a key. Normally, this would
	// be different on a per resource 'group' level, but for the
	// purposes of the majority of the tests, this will be
	// set as a constant.
	private static final String LOAD_BALANCER_KEY = "mytest";

	@Test
	public void testSimpleFairness() throws URISyntaxException {
		List<URI> loadBalancees = simpleTestSetup();
		// Should essentially round-robin alphabetically through the
		// URIs
		Assert.assertEquals(loadBalancees.get(0),
				LoadBalancer.getBestResource(LOAD_BALANCER_KEY));
		Assert.assertEquals(loadBalancees.get(1),
				LoadBalancer.getBestResource(LOAD_BALANCER_KEY));
		Assert.assertEquals(loadBalancees.get(2),
				LoadBalancer.getBestResource(LOAD_BALANCER_KEY));
		// Second loop through URIs
		Assert.assertEquals(loadBalancees.get(0),
				LoadBalancer.getBestResource(LOAD_BALANCER_KEY));
		Assert.assertEquals(loadBalancees.get(1),
				LoadBalancer.getBestResource(LOAD_BALANCER_KEY));
		Assert.assertEquals(loadBalancees.get(2),
				LoadBalancer.getBestResource(LOAD_BALANCER_KEY));

		// Actual usage should cleanup by returning URIs once
		// finished with them, returning once per requested loadbalancee.
		// Example below.
		LoadBalancer.returnResource(LOAD_BALANCER_KEY, loadBalancees.get(0));
		LoadBalancer.returnResource(LOAD_BALANCER_KEY, loadBalancees.get(0));
		LoadBalancer.returnResource(LOAD_BALANCER_KEY, loadBalancees.get(1));
		LoadBalancer.returnResource(LOAD_BALANCER_KEY, loadBalancees.get(1));
		LoadBalancer.returnResource(LOAD_BALANCER_KEY, loadBalancees.get(2));
		LoadBalancer.returnResource(LOAD_BALANCER_KEY, loadBalancees.get(2));
	}

	@Test
	public void testFairnessWhenBalanceesTakeLongTime()
			throws URISyntaxException {
		// This essentially aims to ensure that when certain balancees
		// take a long time, that the other balancees get hit more.
		List<URI> loadBalancees = simpleTestSetup();
		Assert.assertEquals(loadBalancees.get(0),
				LoadBalancer.getBestResource(LOAD_BALANCER_KEY));
		Assert.assertEquals(loadBalancees.get(1),
				LoadBalancer.getBestResource(LOAD_BALANCER_KEY));
		Assert.assertEquals(loadBalancees.get(2),
				LoadBalancer.getBestResource(LOAD_BALANCER_KEY));
		// Second loop through URIs
		Assert.assertEquals(loadBalancees.get(0),
				LoadBalancer.getBestResource(LOAD_BALANCER_KEY));
		Assert.assertEquals(loadBalancees.get(1),
				LoadBalancer.getBestResource(LOAD_BALANCER_KEY));
		Assert.assertEquals(loadBalancees.get(2),
				LoadBalancer.getBestResource(LOAD_BALANCER_KEY));

		// Now start returning URIs as if index one and two (www2 and
		// www3) are fast dealing with the requests
		LoadBalancer.returnResource(LOAD_BALANCER_KEY, loadBalancees.get(1));
		LoadBalancer.returnResource(LOAD_BALANCER_KEY, loadBalancees.get(1));
		LoadBalancer.returnResource(LOAD_BALANCER_KEY, loadBalancees.get(2));
		LoadBalancer.returnResource(LOAD_BALANCER_KEY, loadBalancees.get(2));

		// Since www1 is taking a long time (and the consumer has thus
		// not returned the URIs, it should not come up in the next
		// four results:
		Assert.assertEquals(loadBalancees.get(1),
				LoadBalancer.getBestResource(LOAD_BALANCER_KEY));
		Assert.assertEquals(loadBalancees.get(2),
				LoadBalancer.getBestResource(LOAD_BALANCER_KEY));
		Assert.assertEquals(loadBalancees.get(1),
				LoadBalancer.getBestResource(LOAD_BALANCER_KEY));
		Assert.assertEquals(loadBalancees.get(2),
				LoadBalancer.getBestResource(LOAD_BALANCER_KEY));
		// Should cleanup afterwards. All servers have two requests
		// out to them each at this point, so cleanup two for each server.
		LoadBalancer.returnResource(LOAD_BALANCER_KEY, loadBalancees.get(0));
		LoadBalancer.returnResource(LOAD_BALANCER_KEY, loadBalancees.get(0));
		LoadBalancer.returnResource(LOAD_BALANCER_KEY, loadBalancees.get(1));
		LoadBalancer.returnResource(LOAD_BALANCER_KEY, loadBalancees.get(1));
		LoadBalancer.returnResource(LOAD_BALANCER_KEY, loadBalancees.get(2));
		LoadBalancer.returnResource(LOAD_BALANCER_KEY, loadBalancees.get(2));
	}

	@Test
	public void testMultipleKeyDoesNotCauseConflict() {
		List<URI> normalItems = simpleTestSetup();
		List<URI> otherItems = newKeyTestSetup();
		for (int i = 0; i < 1000; i++) {
			Assert.assertFalse(LoadBalancer.getBestResource(LOAD_BALANCER_KEY)
					.getHost().contains("brucelee"));
			Assert.assertTrue(LoadBalancer.getBestResource(LOAD_BALANCER_KEY)
					.getHost().contains("brucel.ee"));
		}
		for (int i = 0; i < 1000; i++) {
			Assert.assertFalse(LoadBalancer.getBestResource("otherkey")
					.getHost().contains("brucel.ee"));
			Assert.assertTrue(LoadBalancer.getBestResource("otherkey")
					.getHost().contains("brucelee"));
		}
		// Normally, you should clean up. Other tests will just do
		// setup, though, so not going to worry about it.
	}

	@Test
	public void testMultiThreadedBehaviorIsFair() throws InterruptedException {
		simpleTestSetup();
		final AtomicInteger www1 = new AtomicInteger(0);
		final AtomicInteger www2 = new AtomicInteger(0);
		final AtomicInteger www3 = new AtomicInteger(0);

		int numThreads = 100;
		Thread[] threads = new Thread[numThreads];
		for (int i = 0; i < numThreads; i++) {
			threads[i] = new Thread() {
				public void run() {
					Random random = new Random();
					for (int i = 0; i < 100; i++) {
						URI item = LoadBalancer
								.getBestResource(LOAD_BALANCER_KEY);
						if (item.toASCIIString().contains("www1")) {
							www1.incrementAndGet();
						}
						if (item.toASCIIString().contains("www2")) {
							www2.incrementAndGet();
						}
						if (item.toASCIIString().contains("www3")) {
							www3.incrementAndGet();
						}
						try {
							// simulate the consumer doing something with
							// the URI
							Thread.sleep(random.nextInt(50) + 1);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						// Return the URI once done with it.
						LoadBalancer.returnResource(LOAD_BALANCER_KEY, item);
					}
				}
			};
		}
		for (int i = 0; i < numThreads; i++) {
			threads[i].start();
		}
		for (int i = 0; i < numThreads; i++) {
			threads[i].join();
		}
		int nwww1 = www1.get();
		int nwww2 = www2.get();
		int nwww3 = www3.get();

		// Sanity check.
		Assert.assertEquals(10000, nwww1 + nwww2 + nwww3);

		// Why am I doing this strange thing where I'm not checking
		// for all of the numbers being 3333/3334, but instead checking
		// that they are all greater than a number below that?
		// Load balancing will not show exactly even numbers, but should
		// be fair based on the workload that the consumer is doing with the
		// URI.
		// [Fairness determined mainly by how many active requests are on the
		// resource, see LoadBalancee's compareTo for more information]
		// The reason for appearing slightly
		// unfair is that if multiple threads are working with the
		// same URI, then that URI is then less likely to be given
		// to other threads. This, plus intentional randomness,
		// plus randomness due to task scheduling, means that
		// straight equal numbers are not expected (nor desired)
		Assert.assertTrue(nwww1 / 3250 == 1);
		Assert.assertTrue(nwww3 / 3250 == 1);
		Assert.assertTrue(nwww2 / 3250 == 1);

	}

	@Test
	public void testHealthReporting() throws InterruptedException {
		Assume.assumeTrue(false);
		List<URI> balancees = simpleTestSetup();
		// Grab www1 from the list.
		URI toMarkAsUnhealthy = LoadBalancer.getBestResource(LOAD_BALANCER_KEY);
		// Pretend that we attempted to connect to it, and failed.
		// Mark as unhealthy. Also, return the resource to avoid holding onto
		// a permit unnecessarily.
		LoadBalancer.reportUnhealthy(LOAD_BALANCER_KEY, toMarkAsUnhealthy);
		LoadBalancer.returnResource(LOAD_BALANCER_KEY, toMarkAsUnhealthy);

		// Now, try getting URIs from the LoadBalancer. We should not get
		// the unhealthy resource back.
		for (int i = 0; i < 10000; i++) {
			URI toTest = LoadBalancer.getBestResource(LOAD_BALANCER_KEY);
			Assert.assertTrue(!toMarkAsUnhealthy.equals(toTest));
			LoadBalancer.returnResource(LOAD_BALANCER_KEY, toTest);
		}
		// This behavior should persist for 30s, after which the load balancer
		// will allow the consumer to request the item again.
		Thread.sleep(30000);
		boolean nowHealthyItemFound = false;
		for (int i = 0; i < 3; i++) {
			URI toCompare = LoadBalancer.getBestResource(LOAD_BALANCER_KEY);
			if (toMarkAsUnhealthy.equals(toCompare)) {
				nowHealthyItemFound = true;
			}
		}
		Assert.assertTrue(nowHealthyItemFound);
	}

	@Test
	public void testSimpleStickySessions() {
		List<URI> simpleTestSetup = simpleTestSetup();
		StickySessionStrategy strategy = new StickySessionStrategy() {

			public URI giveURIByStrategy(String stickySessionKey, URI[] uris) {
				// Always give the first one
				return uris[0];
			}

		};
		URI myURI = LoadBalancer.getStickyURI(LOAD_BALANCER_KEY,
				"myStickySessionIdentifier", strategy);
		for (int i = 0; i < 20; i++) {
			Assert.assertEquals(myURI, LoadBalancer.getStickyURI(
					LOAD_BALANCER_KEY, "myStickySessionIdentifier", strategy));
		}
		// Now, we've taken 21 instances of that sticky URI. If we pull the next
		// 42 URIs off of the LoadBalancer fairly, we shouldn't get the sticky
		// URI as the sticky behavior also participates in the fairness
		// principles.
		for (int i = 0; i < 42; i++) {
			Assert.assertTrue(!myURI.equals(LoadBalancer
					.getBestResource(LOAD_BALANCER_KEY)));
		}
	}

	@Test
	public void testShowAnotherStickyStrategy() {
		List<URI> simpleTestSetup = simpleTestSetup();
		StickySessionStrategy strategy = new StickySessionStrategy() {

			public URI giveURIByStrategy(String stickySessionKey, URI[] uris) {
				for (URI uri : uris) {
					if (uri.toASCIIString().contains(stickySessionKey)) {
						return uri;
					}
				}
				return null;
			}

		};
		URI myURI = LoadBalancer.getStickyURI(LOAD_BALANCER_KEY, "www1",
				strategy);
		for (int i = 0; i < 20; i++) {
			Assert.assertEquals(myURI, LoadBalancer.getStickyURI(
					LOAD_BALANCER_KEY, "www1", strategy));
		}
	}

	@Test
	public void testShowStringHashingStickyStrategy() {
		List<URI> simpleTestSetup = simpleTestSetup();
		StickySessionStrategy strategy = new StickySessionStrategy() {

			public URI giveURIByStrategy(String stickySessionKey, URI[] uris) {
				// intentionally throw NPE if stickySessionKey is null
				return uris[Math.abs(stickySessionKey.hashCode()) % uris.length];
			}

		};
		URI myFirstURI = LoadBalancer.getStickyURI(LOAD_BALANCER_KEY,
				"myFirstURI", strategy);
		URI anotherURI = LoadBalancer.getStickyURI(LOAD_BALANCER_KEY,
				"anotherURI", strategy);

		// Test will only remain valid as long as string hashing makes the
		// hashcodes mod differently over three. This is the case on my machine
		// currently.
		Assert.assertTrue(!myFirstURI.equals(anotherURI));
	}

	@Test
	public void testSimpleWorkStrategy(){
		List<URI> simpleTestSetup = simpleTestSetup();
		ResourceWorkStrategy<String> strategy = new ResourceWorkStrategy<String>(){
			public String processWork(URI uri, Map<String, Object> parameters) throws ResourceUnhealthyException, IllegalArgumentException {
				return "Hello World";
			}
		};

		Assert.assertEquals("Hello World", LoadBalancer.doWorkOnBestBalancee(LOAD_BALANCER_KEY, 
			strategy, null));
	}

	@Test
	public void testGetWebWorkStrategy(){
		List<URI> uris = realURITestSetup();
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("PATH", "/movies");		
		ResourceWorkStrategy<String> strategy = new WebGetStrategy();
		//Assume that Google will always have a link to YouTube on every page
		Assert.assertTrue(LoadBalancer.doWorkOnBestBalancee(LOAD_BALANCER_KEY, 
			strategy, parameters).contains("YouTube"));
	}

	@Test
	public void testUnhealthyStickySituation(){
		List<URI> uris = realURITestSetup();
		ResourceWorkStrategy<Object> markUnhealthyStrategy = new MarkGoogleUnhealthyStrategy();

		StickySessionStrategy pickFirstResource = new StickySessionStrategy() {
			public URI giveURIByStrategy(String stickySessionKey, URI[] uris) {
				//Let's always give the first one! That's a good strategy,
				//right?
				return uris[0];
			}

		};

		try{
			LoadBalancer.doWorkOnStickyBalancee(LOAD_BALANCER_KEY, 
				markUnhealthyStrategy, null, pickFirstResource, null);
		}catch(ResourceUnhealthyException rue){
			//This should definitely happen. We're intentionally causing it.
		}
		//At this point, the only URI in the load balancer set should be
		//marked as unhealthy.

		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("PATH", "/movies");		
		ResourceWorkStrategy<String> strategy = new WebGetStrategy();

		//Since this is a sticky situation, it will still attempt to
		//run against the returned resource.
		String result = "";
		try{
			result = LoadBalancer.doWorkOnStickyBalancee(LOAD_BALANCER_KEY, 
				strategy, parameters, pickFirstResource, null);
		}catch (ResourceUnhealthyException rue){
			Assert.fail("Unless google.com/movies is down, shouldn't get here");
		}
		Assert.assertTrue(result.contains("YouTube"));
	}

	private List<URI> simpleTestSetup() {
		List<URI> loadBalancees = new ArrayList<URI>();
		try {
			loadBalancees.add(new URI("http://www1.brucel.ee"));
			loadBalancees.add(new URI("http://www2.brucel.ee"));
			loadBalancees.add(new URI("http://www3.brucel.ee"));
		} catch (URISyntaxException e) {
			Assert.fail();
		}
		LoadBalancer.setupTest(loadBalancees, LOAD_BALANCER_KEY);
		return loadBalancees;
	}

	private List<URI> realURITestSetup(){
		List<URI> loadBalancees = new ArrayList<URI>();
		try{
			loadBalancees.add(new URI("http://www.google.com"));
		}catch(URISyntaxException e){
			Assert.fail();
		}
		LoadBalancer.setupTest(loadBalancees, LOAD_BALANCER_KEY);
		return loadBalancees;
	}

	private List<URI> newKeyTestSetup() {
		List<URI> loadBalancees = new ArrayList<URI>();
		try {
			loadBalancees.add(new URI("http://www1.brucelee.com"));
			loadBalancees.add(new URI("http://www2.brucelee.com"));
			loadBalancees.add(new URI("http://www3.brucelee.com"));
		} catch (URISyntaxException e) {
			Assert.fail();
		}
		LoadBalancer.initializeGroup(loadBalancees, "otherkey");
		return loadBalancees;
	}

	private class MarkGoogleUnhealthyStrategy implements ResourceWorkStrategy<Object> {
		public Object processWork(URI uri, Map<String, Object> parameters) throws ResourceUnhealthyException, IllegalArgumentException {
			if (uri.toASCIIString().contains("google")){
				throw new ResourceUnhealthyException("Spurious google unhealthy exception");
			}
			return null;
		}

	}

	private class WebGetStrategy implements ResourceWorkStrategy<String> {
		public String processWork(URI uri, Map<String, Object> parameters) throws ResourceUnhealthyException, IllegalArgumentException {
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
		    	throw new ResourceUnhealthyException("Test had an issue getting data from " + attemptURL, ioe);
		    } finally {
		        try {
		            is.close();
		        } catch (IOException ioe) {
		            // nothing to see here
		        }
		    }
			return sb.toString();
		}
	}
}
