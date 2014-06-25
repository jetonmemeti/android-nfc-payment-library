package ch.uzh.csg.paymentlib;

import ch.uzh.csg.mbps.customserialization.Currency;

/**
 * The user of this interface can check if the application user accepted or
 * rejected a payment request (by clicking the appropriate button on the UI).
 * 
 * @author Jeton Memeti
 * 
 */
public interface IUserPromptPaymentRequest {

	/**
	 * Returns the application user's decision if he wants to accept or reject
	 * the payment with the given parameters.
	 * 
	 * @param username
	 *            the payee's username
	 * @param currency
	 *            the payment {@link Currency}
	 * @param amount
	 *            the payment amount in the given currency
	 * @return true if the user accepts, false if he rejects
	 */
	public void promptUserPaymentRequest(String username, Currency currency, long amount, Answer answer);
	
	/**
	 * Returns the application user's decision which has been asked already
	 * before (see getPaymentRequestAnswer). This avoids prompting the user
	 * again (e.g., with a dialog) and returns the already entered decision.
	 * 
	 * @return true if the user accepted the payment, false otherwise
	 */
	public boolean isPaymentAccepted();
	
}
