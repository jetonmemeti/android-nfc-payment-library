package ch.uzh.csg.paymentlib.exceptions;

import ch.uzh.csg.paymentlib.messages.PaymentError;

/**
 * This Exception is thrown when a {@link PaymentError} with an unknown code is
 * tried to be created.
 * 
 * @author Jeton Memeti
 * 
 */
public class UnknownPaymentErrorException extends Exception {
	
	private static final long serialVersionUID = 5122523954735347983L;

}
