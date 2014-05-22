package ch.uzh.csg.paymentlib.exceptions;

import ch.uzh.csg.paymentlib.paymentrequest.SignatureAlgorithm;

/**
 * This Exception is thrown when a {@link SignatureAlgorithm} with an unknown code is
 * tried to be created.
 * 
 * @author Jeton Memeti
 * 
 */
public class UnknownSignatureAlgorithmException extends Exception {
	
	private static final long serialVersionUID = 2519179640406402742L;

}
