package ee.brucel.loadbalance;

import java.net.URI;
import java.util.concurrent.Semaphore;

class LoadBalancee implements Comparable<LoadBalancee> {
	private Semaphore permits = null;
	private URI resource = null;
	private long lastUsed = -1;

	public LoadBalancee(URI resourceToDistribute) {
		permits = new Semaphore(1000);
		resource = resourceToDistribute;
	}

	public URI acquirePermit() {
		while (!permits.tryAcquire()) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		lastUsed = System.nanoTime();
		return resource;
	}

	public URI getResource() {
		return resource;
	}

	public int availablePermits() {
		return permits.availablePermits();
	}

	public void releasePermit() {
		permits.release();
	}

	public void addBackPermits() {
		permits.release(1000);
	}

	// Used to ensure that SortedSets of LoadBalancees will
	// be ordered such that earlier items in the set have
	// more available permits, and thus are, as best we can tell,
	// less busy at time of request.
	public int compareTo(LoadBalancee other) {
		if (this == other) {
			return 0;
		}
		if (this.availablePermits() > other.availablePermits()) {
			return -1;
		}
		if (this.availablePermits() < other.availablePermits()) {
			return 1;
		}
		if (this.lastUsed > other.lastUsed) {
			return 1;
		}
		if (this.lastUsed < other.lastUsed) {
			return -1;
		}
		int uriComparison = this.resource.compareTo(other.resource);
		if (uriComparison != 0) {
			return uriComparison;
		}
		return 0;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((permits == null) ? 0 : permits.hashCode());
		result = prime * result
				+ ((resource == null) ? 0 : resource.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LoadBalancee other = (LoadBalancee) obj;
		if (permits == null) {
			if (other.permits != null)
				return false;
		} else if (!permits.equals(other.permits))
			return false;
		if (resource == null) {
			if (other.resource != null)
				return false;
		} else if (!resource.equals(other.resource))
			return false;
		return true;
	}

}
