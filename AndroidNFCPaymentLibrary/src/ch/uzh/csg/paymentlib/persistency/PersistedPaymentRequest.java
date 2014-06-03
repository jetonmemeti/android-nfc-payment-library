package ch.uzh.csg.paymentlib.persistency;

import ch.uzh.csg.mbps.customserialization.Currency;
import ch.uzh.csg.mbps.customserialization.exceptions.UnknownCurrencyException;

//TODO: javadoc
public class PersistedPaymentRequest {
	private String username;
	private byte currencyCode;
	private long amount;
	private long timestamp;
	
	//TODO: set limit to timestamp validation (on server, then delete on shared prefs)
	
	public PersistedPaymentRequest(String username, Currency currency, long amount, long timestamp) {
		this.username = username;
		this.currencyCode = currency.getCode();
		this.amount = amount;
		this.timestamp = timestamp;
	}

	public String getUsername() {
		return username;
	}

	public Currency getCurrency() throws UnknownCurrencyException {
		return Currency.getCurrency(currencyCode);
	}

	public long getAmount() {
		return amount;
	}

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
	
}
