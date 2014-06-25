package ch.uzh.csg.paymentlib.container;

import ch.uzh.csg.mbps.customserialization.Currency;
import ch.uzh.csg.mbps.customserialization.PaymentRequest;
import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;

/**
 * This class contains the payment information for a {@link PaymentRequest}.
 * 
 * @author Jeton Memeti
 * 
 */
public class PaymentInfos {
	
	private Currency currency;
	private long amount;
	private long timestamp;
	
	/**
	 * Instantiates a new object. Sets the timestamp to the current time.
	 * 
	 * @param currency
	 *            the {@link Currency} of the payment
	 * @param amount
	 *            the amount of the payment in the indicated {@link Currency}
	 * @throws IllegalArgumentException
	 *             if any parameter is not valid (e.g., null or negative)
	 */
	public PaymentInfos(Currency currency, long amount) throws IllegalArgumentException {
		this(currency, amount, System.currentTimeMillis());
	}
	
	/**
	 * Instantiates a new object.
	 * 
	 * @param currency
	 *            the {@link Currency} of the payment
	 * @param amount
	 *            the amount of the payment in the indicated {@link Currency}
	 * @param timestamp
	 *            the timestamp of the payment request (needed to avoid
	 *            double-spending on the server)
	 * @throws IllegalArgumentException
	 *             if any parameter is not valid (e.g., null or negative)
	 */
	public PaymentInfos(Currency currency, long amount, long timestamp) throws IllegalArgumentException {
		checkParams(currency, amount, timestamp);
		
		this.currency = currency;
		this.amount = amount;
		this.timestamp = timestamp;
	}

	private void checkParams(Currency currency, long amount, long timestamp) throws IllegalArgumentException {
		if (currency == null)
			throw new IllegalArgumentException("The currency cannot be null.");
		
		if (amount <= 0)
			throw new IllegalArgumentException("The amount must be greatern than 0.");
		
		if (timestamp <= 0)
			throw new IllegalArgumentException("The timestamp must be greatern than 0.");
	}
	
	/**
	 * Returns the currency.
	 */
	public Currency getCurrency() {
		return currency;
	}

	/**
	 * Returns the amount.
	 */
	public long getAmount() {
		return amount;
	}

	/**
	 * Returs the timestamp.
	 */
	public long getTimestamp() {
		return timestamp;
	}
	
}
