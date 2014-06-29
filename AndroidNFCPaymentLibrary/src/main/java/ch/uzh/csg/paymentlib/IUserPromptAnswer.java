package ch.uzh.csg.paymentlib;

/**
 * The implementation of this interface is notified if a user accepted or
 * rejected a payment (e.g, by clicking on a button) after a payment request has
 * been sent from the {@link PaymentRequestInitializer} to the
 * {@link PaymentRequestHandler}. This is only relevant for the use case where
 * the payee initiates a payment request and the payer has explicitly to agree
 * or disagree.
 * 
 * @author Jeton
 * 
 */
public interface IUserPromptAnswer {
	
	/**
	 * When this method is called, the payer has accepted the payment request.
	 * The implementation needs to decide what needs to be done next.
	 */
	public abstract void acceptPayment();
	
	/**
	 * When this method is called, the payer has rejected the payment request.
	 * The implementation needs to decide what needs to be done next.
	 */
	public abstract void rejectPayment();
	
}
