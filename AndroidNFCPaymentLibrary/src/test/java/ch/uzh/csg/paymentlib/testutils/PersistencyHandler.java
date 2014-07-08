package ch.uzh.csg.paymentlib.testutils;

import java.util.ArrayList;

import ch.uzh.csg.mbps.customserialization.Currency;
import ch.uzh.csg.mbps.customserialization.exceptions.UnknownCurrencyException;
import ch.uzh.csg.paymentlib.persistency.IPersistencyHandler;
import ch.uzh.csg.paymentlib.persistency.PersistedPaymentRequest;

public class PersistencyHandler implements IPersistencyHandler {
	private ArrayList<PersistedPaymentRequest> list = new ArrayList<PersistedPaymentRequest>();
	
	@Override
	public PersistedPaymentRequest getPersistedPaymentRequest(String username, Currency currency, long amount) {
		try {
			for (PersistedPaymentRequest request : list) {
				if (request.getUsername().equals(username) && request.getCurrency().getCode() == currency.getCode() && request.getAmount() == amount) {
					return request;
				}
			}
		} catch (UnknownCurrencyException e) {
		}
		return null;
	}
	
	@Override
	public void addPersistedPaymentRequest(PersistedPaymentRequest paymentRequest) {
		boolean exists = false;
		for (PersistedPaymentRequest request : list) {
			if (request.equals(paymentRequest)) {
				exists = true;
				break;
			}
		}
		if (!exists) {
			list.add(paymentRequest);
		}
	}
	
	@Override
	public void deletePersistedPaymentRequest(PersistedPaymentRequest paymentRequest) {
		for (int i=0; i<list.size(); i++) {
			PersistedPaymentRequest request = list.get(i);
			if (request.equals(paymentRequest)) {
				list.remove(i);
				break;
			}
		}
	}
	
	public ArrayList<PersistedPaymentRequest> getList() {
		return list;
	}
	
}
