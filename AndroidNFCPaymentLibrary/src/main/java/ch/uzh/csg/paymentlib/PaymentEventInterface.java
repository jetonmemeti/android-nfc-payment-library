package ch.uzh.csg.paymentlib;

public interface PaymentEventInterface {

	public abstract void handleMessage(PaymentEvent event, Object object);

}