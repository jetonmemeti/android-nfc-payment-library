package ch.uzh.csg.paymentlib;

import ch.uzh.csg.paymentlib.payment.Currency;

//TODO: javadoc
public interface IUserPromptPaymentRequest {

	//blocking! avoid long calculations
	public boolean handlePaymentRequest(String username, Currency currency, long amount);
	
}
