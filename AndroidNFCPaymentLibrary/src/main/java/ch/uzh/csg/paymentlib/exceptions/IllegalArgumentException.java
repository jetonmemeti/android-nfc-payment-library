package ch.uzh.csg.paymentlib.exceptions;

/**
 * In contrast to {@link IllegalArgumentException} this extends
 * {@link Exception} and is therefore caught. Therefore, it has explicitly to be
 * caught when calling methods which might throw it.
 * 
 * @author Jeton Memeti
 * 
 */
public class IllegalArgumentException extends Exception {
	private static final long serialVersionUID = 4916228397307625804L;
	
	public IllegalArgumentException(String s) {
		super(s);
	}

}
