package ch.uzh.csg.paymentlib;

/**
 * The implementation of this interface gets notified on every
 * {@link PaymentEvent}, similar to the observer pattern. The implementation
 * must decide what to do on a given event.
 * 
 * Some events might send additional data which gives additional information
 * about the event (e.g., the message received if event ==
 * PaymentEvent.MESSAGE_RECEIVED).
 * 
 * @author Jeton Memeti
 * 
 */
public interface IPaymentEventHandler {

	/**
	 * Sends the {@link PaymentEvent} to the user and gives optional additional
	 * information if the given {@link PaymentEvent} requires it.
	 * 
	 * @param event
	 *            the {@link PaymentEvent} to propagate
	 * @param object
	 *            the optional data
	 */
	public void handleMessage(PaymentEvent event, Object object);
	
	/**
	 * Sends the {@link PaymentEvent} to the user and gives optional additional
	 * information if the given {@link PaymentEvent} requires it. This method is
	 * only meant to be used with PaymentEvent.FORWARD_TO_SERVER. The caller can
	 * then be notified when the server response arrives.
	 * 
	 * @param event
	 *            the PaymentEvent.FORWARD_TO_SERVER
	 * @param object
	 *            the data to forward to the server
	 * @param caller
	 *            the {@link IServerResponseListener} to be notified when the
	 *            server response arrives
	 */
	public void handleMessage(PaymentEvent event, Object object, IServerResponseListener caller);

}