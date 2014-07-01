package ch.uzh.csg.paymentlib;

import ch.uzh.csg.paymentlib.messages.PaymentMessage;

/**
 * This defines all the different events which might occur during the payment
 * protocol. This is also the header of a {@link PaymentMessage}.
 * 
 * @author Jeton Memeti
 * 
 */
public enum PaymentEvent {
	ERROR,
	SUCCESS,
	/*
	 * provide the message to forward to the server as well as the caller so it
	 * can be notified as soon as the server response arrives
	 */
	FORWARD_TO_SERVER,
	/*
	 * This event is propagated whenever a payment is initialized. It is meant
	 * to be used on the UI to show for example a progress dialog.
	 */
	INITIALIZED;
}
