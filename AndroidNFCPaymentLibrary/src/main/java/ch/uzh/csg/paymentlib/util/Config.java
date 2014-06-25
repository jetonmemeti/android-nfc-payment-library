package ch.uzh.csg.paymentlib.util;

/**
 * This class contains settings for the payment library.
 * 
 * @author Jeton Memeti
 * 
 */
public class Config {
	
	//TODO jeton: is this needed? should be handled by spring anyway!
	public static final long SERVER_CALL_TIMEOUT = 3 * 1000; //in ms - server call
	public static final long SERVER_RESPONSE_TIMEOUT = 4 * 1000; //in ms - PaymentRequestHandler waiting for server response

}
