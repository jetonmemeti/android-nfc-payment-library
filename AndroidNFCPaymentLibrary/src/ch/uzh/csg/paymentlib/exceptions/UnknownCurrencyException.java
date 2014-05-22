package ch.uzh.csg.paymentlib.exceptions;

import ch.uzh.csg.paymentlib.paymentrequest.Currency;

/**
 * This Exception is thrown when a {@link Currency} with an unknown code is
 * tried to be created.
 * 
 * @author Jeton Memeti
 * 
 */
public class UnknownCurrencyException extends Exception {
	
	private static final long serialVersionUID = -7110301898988281908L;
	
}
