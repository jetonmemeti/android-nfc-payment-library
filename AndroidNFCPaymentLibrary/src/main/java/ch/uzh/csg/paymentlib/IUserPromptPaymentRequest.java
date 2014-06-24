package ch.uzh.csg.paymentlib;

import ch.uzh.csg.mbps.customserialization.Currency;

//TODO: javadoc
public interface IUserPromptPaymentRequest {

	public boolean getPaymentRequestAnswer(String username, Currency currency, long amount);
	
	//blocking! avoid long calculations
	public boolean isPaymentAccepted();
	
	
}
