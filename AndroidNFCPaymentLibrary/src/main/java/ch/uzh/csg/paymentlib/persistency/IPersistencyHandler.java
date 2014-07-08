package ch.uzh.csg.paymentlib.persistency;

import ch.uzh.csg.mbps.customserialization.Currency;

/**
 * This interface has to be implemented by the class responsible for persisting
 * the user information on the local device (e.g., on the SharedPreferences).
 * 
 * @author Jeton Memeti
 * 
 */
public interface IPersistencyHandler {
	
	/**
	 * Returns a {@link PersistedPaymentRequest} based on the given parameters.
	 * If no {@link PersistedPaymentRequest} if the given parameters is found,
	 * null is returned.
	 * 
	 * @param username
	 *            the username of the payee
	 * @param currency
	 *            the currency
	 * @param amount
	 *            the amount
	 */
	public PersistedPaymentRequest getPersistedPaymentRequest(String username, Currency currency, long amount);
	
	/**
	 * Persists a {@link PersistedPaymentRequest} on the local device.
	 */
	public void addPersistedPaymentRequest(PersistedPaymentRequest paymentRequest);

	/**
	 * Deletes a {@link PersistedPaymentRequest} from the local device. If there
	 * is no such {@link PersistedPaymentRequest} persisted, nothing should
	 * happen. The implementation should use the equals method of
	 * {@link PersistedPaymentRequest} to find the object to delete.
	 */
	public void deletePersistedPaymentRequest(PersistedPaymentRequest paymentRequest);
	
}
