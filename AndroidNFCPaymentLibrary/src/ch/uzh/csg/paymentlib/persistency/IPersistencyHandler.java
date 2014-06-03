package ch.uzh.csg.paymentlib.persistency;

/**
 * This interface has to be implemented by the class responsible for persisting
 * the user information on the local device (e.g., on the SharedPreferences).
 * 
 * @author Jeton Memeti
 * 
 */
public interface IPersistencyHandler {
	
	/**
	 * Checks if the given {@link PersistedPaymentRequest} is already persisted or
	 * not. The implementation should use the equals method of
	 * {@link PersistedPaymentRequest} for comparison.
	 */
	public boolean exists(PersistedPaymentRequest paymentRequest);
	
	/**
	 * Persists a {@link PersistedPaymentRequest} on the local device.
	 */
	public void add(PersistedPaymentRequest paymentRequest);

	/**
	 * Deletes a {@link PersistedPaymentRequest} from the local device. The
	 * implementation should use the equals method of
	 * {@link PersistedPaymentRequest} to find the object to delete.
	 */
	public void delete(PersistedPaymentRequest paymentRequest);
	
}
