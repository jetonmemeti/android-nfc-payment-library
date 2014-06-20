package ch.uzh.csg.paymentlib;

//TODO: javadoc
public interface IPaymentEventHandler {

	public void handleMessage(PaymentEvent event, Object object);
	
	public void handleMessage(PaymentEvent event, Object object, IServerResponseListener caller);

}