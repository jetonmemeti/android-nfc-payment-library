package ch.uzh.csg.paymentlib.container;

import java.security.PrivateKey;
import java.util.Random;

import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;

/**
 * This class contains the user information. The user is the currently
 * authenticated user in your application.
 * 
 * @author Jeton Memeti
 * 
 */
public class UserInfos {
	
	private String username;
	private PrivateKey privateKey;
	private PKIAlgorithm pkiAlgorithm;
	private int keyNumber;
	private long userId;
	
	/**
	 * Instantiates a new object.
	 * 
	 * @param username
	 *            the username of this user
	 * @param privateKey
	 *            the user's private key needed to sign payment requests
	 * @param pkiAlgorithm
	 *            the user's {@link PKIAlgorithm} used for key generation and
	 *            signature algorithm so that the server can verify the
	 *            signature
	 * @param keyNumber
	 *            the key number of the given private key needed to indicate
	 *            which key was used for signing so that the server can verify
	 *            the signature
	 * @throws IllegalArgumentException
	 *             if any parameter is not valid (e.g., null)
	 */
	public UserInfos(String username, PrivateKey privateKey, PKIAlgorithm pkiAlgorithm, int keyNumber) throws IllegalArgumentException {
		checkParams(username, privateKey, pkiAlgorithm, keyNumber);
		
		this.username = username;
		this.privateKey = privateKey;
		this.pkiAlgorithm = pkiAlgorithm;
		this.keyNumber = keyNumber;
		this.userId = new Random().nextLong();
	}
	
	private void checkParams(String username, PrivateKey privateKey, PKIAlgorithm pkiAlgorithm, int keyNumber) throws IllegalArgumentException {
		if (username == null || username.isEmpty() || username.length() > 255)
			throw new IllegalArgumentException("The username cannot be null, empty, or longer than 255 characters.");
		
		if (privateKey == null)
			throw new IllegalArgumentException("The privatekey cannot be null.");
			
		if (pkiAlgorithm == null)
			throw new IllegalArgumentException("The pki algorithm cannot be null or empty.");
		
		if (keyNumber <= 0 || keyNumber > 255)
			//TB: TODO: why is 0 not valid?
			throw new IllegalArgumentException("The key number must be between 1 and 255.");
	}

	/**
	 * Returns the username.
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Returns the private key.
	 */
	public PrivateKey getPrivateKey() {
		return privateKey;
	}

	/**
	 * Returns the {@link PKIAlgorithm}
	 */
	public PKIAlgorithm getPKIAlgorithm() {
		return pkiAlgorithm;
	}

	/**
	 * Returns the key number.
	 */
	public int getKeyNumber() {
		return keyNumber;
	}

	/**
	 * Returns the user id. The user id is randomly assigned on every
	 * instantiation! It is needed for the underlying NFC Library to detect if
	 * the same parties are communication and to detect NFC session resumes.
	 */
	public long getUserId() {
		return userId;
	}

}
