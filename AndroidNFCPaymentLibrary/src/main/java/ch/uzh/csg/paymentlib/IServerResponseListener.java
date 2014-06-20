package ch.uzh.csg.paymentlib;

import ch.uzh.csg.mbps.customserialization.ServerPaymentResponse;

//TODO: javadoc
public interface IServerResponseListener {
	
	public void onServerResponse(ServerPaymentResponse serverPaymentResponse);

}
