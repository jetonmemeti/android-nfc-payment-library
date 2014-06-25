package ch.uzh.csg.paymentlib;

import ch.uzh.csg.mbps.customserialization.ServerPaymentRequest;
import ch.uzh.csg.mbps.customserialization.ServerPaymentResponse;

/**
 * The user can send a {@link ServerPaymentResponse} to the implementation of
 * this interface for further processing, after a {@link ServerPaymentRequest}
 * has been send to the server and the response has arrived.
 * 
 * @author Jeton Memeti
 * 
 */
public interface IServerResponseListener {
	
	/**
	 * Once the {@link ServerPaymentResponse} arrives, it has to be forwarded to
	 * the caller for further processing.
	 * 
	 * @param serverPaymentResponse
	 *            the server's payment response
	 */
	public void onServerResponse(ServerPaymentResponse serverPaymentResponse);

}
