package ch.uzh.csg.paymentlib.container;

import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;
import ch.uzh.csg.paymentlib.payment.Currency;

//TODO: javadoc
public class PaymentInfos {
	
	private Currency currency;
	private long amount;
	private long timestamp;
	
	public PaymentInfos(Currency currency, long amount) throws IllegalArgumentException {
		checkParams(currency, amount);
		
		this.currency = currency;
		this.amount = amount;
		this.timestamp = 0;
	}
	
	public PaymentInfos(Currency currency, long amount, long timestamp) throws IllegalArgumentException {
		checkParams(currency, amount, timestamp);
		
		this.currency = currency;
		this.amount = amount;
		this.timestamp = timestamp;
	}

	private void checkParams(Currency currency, long amount) throws IllegalArgumentException {
		if (currency == null)
			throw new IllegalArgumentException("The currency cannot be null.");
		
		if (amount <= 0)
			throw new IllegalArgumentException("The amount must be greatern than 0.");
	}
	
	private void checkParams(Currency currency, long amount, long timestamp) throws IllegalArgumentException {
		if (currency == null)
			throw new IllegalArgumentException("The currency cannot be null.");
		
		if (amount <= 0)
			throw new IllegalArgumentException("The amount must be greatern than 0.");
		
		if (timestamp <= 0)
			throw new IllegalArgumentException("The amount must be greatern than 0.");
	}
	
	public Currency getCurrency() {
		return currency;
	}
	
	public long getAmount() {
		return amount;
	}
	
	public long getTimestamp() {
		return timestamp;
	}
	
}
