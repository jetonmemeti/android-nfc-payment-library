package ch.uzh.csg.paymentlib.persistency;

import ch.uzh.csg.mbps.customserialization.Currency;
import ch.uzh.csg.mbps.customserialization.exceptions.UnknownCurrencyException;

/**
 * A persisted payment request is a payment request which is stored on the
 * device's local storage. If after a payment request there does not arrive the
 * server response (being accepted or refused by the server), this payment
 * request has to be stored locally.
 * 
 * If at a later time the same payee is requesting the same amount (in the same
 * currency), than this persisted payment request has to be retrieved from the
 * local storage and sent to the server. The reason is the following: If the
 * payee does not forward the server response or the server response is lost,
 * than resending the same request might result in booking this transaction
 * twice. The timestamp is there to prevent this scenario. The persisted payment
 * request is there to store a payment request with the given timestamp and to
 * reload it for further attempts.
 * 
 * @author Jeton Memeti
 * 
 */
public class PersistedPaymentRequest {
	private String username;
	private byte currencyCode;
	private long amount;
	private long timestamp;
	
	/**
	 * Instantiates a new object.
	 * 
	 * @param username
	 *            the payee's username
	 * @param currency
	 *            the {@link Currency}
	 * @param amount
	 *            the amount in the indicated {@link Currency}
	 * @param timestamp
	 *            the timestamp
	 */
	public PersistedPaymentRequest(String username, Currency currency, long amount, long timestamp) {
		this.username = username;
		this.currencyCode = currency.getCode();
		this.amount = amount;
		this.timestamp = timestamp;
	}

	/**
	 * Returns the payee's username.
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Returns the currency.
	 * 
	 * @throws UnknownCurrencyException
	 *             if the persisted currency code cannot be mapped to an actual
	 *             {@link Currency}
	 */
	public Currency getCurrency() throws UnknownCurrencyException {
		return Currency.getCurrency(currencyCode);
	}

	/**
	 * Returns the amount.
	 */
	public long getAmount() {
		return amount;
	}

	/**
	 * Returns the timestamp.
	 */
	public long getTimestamp() {
		return timestamp;
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (!(o instanceof PersistedPaymentRequest))
			return false;
		
		PersistedPaymentRequest pr = (PersistedPaymentRequest) o;
		
		if (!this.username.equals(pr.username))
			return false;
		if (this.currencyCode != pr.currencyCode)
			return false;
		if (this.amount != pr.amount)
			return false;
		
		return true;
	}
	
	@Override
	public int hashCode() {
	    return username.hashCode() ^ currencyCode ^ Long.valueOf(amount).hashCode();
	}
	
}
