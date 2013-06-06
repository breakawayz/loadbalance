package ee.brucel.loadbalance;

import java.net.URI;

public interface StickySessionStrategy {
	URI giveURIByStrategy(String stickySessionKey, URI[] uris);
}
