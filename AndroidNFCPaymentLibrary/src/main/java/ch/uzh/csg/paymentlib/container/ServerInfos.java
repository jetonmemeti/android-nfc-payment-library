package ch.uzh.csg.paymentlib.container;

import java.security.PublicKey;

import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;

/**
 * This class contains the server information of this user's main server (where
 * he has registered an account for payments). The server is responsible for
 * definitely accepting or refusing a payment.
 * 
 * @author Jeton Memeti
 * 
 */
public class ServerInfos {
	
	private PublicKey publicKey;
	
	/**
	 * Instantiates a new object.
	 * 
	 * @param publicKey
	 *            the server's public key
	 * @throws IllegalArgumentException
	 *             if the public key is null
	 */
	public ServerInfos(PublicKey publicKey) throws IllegalArgumentException {
		if (publicKey == null)
			throw new IllegalArgumentException("The privatekey cannot be null.");
		
		this.publicKey = publicKey;
	}

	/**
	 * Returns the server's public key.
	 */
	public PublicKey getPublicKey() {
		return publicKey;
	}

}
