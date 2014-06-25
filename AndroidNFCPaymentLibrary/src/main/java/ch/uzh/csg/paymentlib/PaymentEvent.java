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
	 * when no server response received (neither ok nor nok) --> show on gui
	 */
	NO_SERVER_RESPONSE;
}
