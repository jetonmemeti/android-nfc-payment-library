package ch.uzh.csg.paymentlib;

public interface PaymentEventInterface {

	public void handleMessage(PaymentEvent event, Object object);
	
	public void handleMessage(PaymentEvent event, Object object, IServerResponseListener caller);

}