package ee.brucel.loadbalance;

import java.net.URI;
import java.util.Map;

public interface ResourceWorkStrategy<T> {
	T processWork(URI uri, Map<String, Object> parameters) throws ResourceUnhealthyException, IllegalArgumentException;
}