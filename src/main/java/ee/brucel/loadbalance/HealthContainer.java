package ee.brucel.loadbalance;

import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

class HealthContainer {
	private long lastRefreshed = -1;
	private Map<URI, Date> unhealthyItems = new HashMap<URI, Date>();

	public void markAsUnhealthy(URI unhealthyResource) {
		removeStaleEntries();
		unhealthyItems.put(unhealthyResource, new Date());
	}

	private void removeStaleEntries() {
		long currTime = (new Date()).getTime();
		if (currTime - lastRefreshed > 1000) {
			for (Map.Entry<URI, Date> entry : unhealthyItems.entrySet()) {
				if (currTime - entry.getValue().getTime()
						+ (currTime - lastRefreshed) > 30000) {
					unhealthyItems.remove(entry.getKey());
				}
			}
			lastRefreshed = currTime;
		}
	}

	public boolean isURIUnhealthy(URI resource) {
		removeStaleEntries();
		return unhealthyItems.keySet().contains(resource);
	}
}
