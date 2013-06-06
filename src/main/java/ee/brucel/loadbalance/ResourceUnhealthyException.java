package ee.brucel.loadbalance;

public class ResourceUnhealthyException extends Exception{
	public ResourceUnhealthyException(String message){
		super(message);
	}
	public ResourceUnhealthyException(String message, Throwable cause){
		super(message, cause);
	}
}