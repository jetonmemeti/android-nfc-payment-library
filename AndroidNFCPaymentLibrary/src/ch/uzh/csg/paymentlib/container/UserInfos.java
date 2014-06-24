package ch.uzh.csg.paymentlib.container;

import java.security.PrivateKey;
import java.util.Random;

import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;

//TODO: javadoc
public class UserInfos {
	
	private String username;
	private PrivateKey privateKey;
	private PKIAlgorithm pkiAlgorithm;
	private int keyNumber;
	private long userId;
	
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
			throw new IllegalArgumentException("The key number must be between 1 and 255.");
	}

	public String getUsername() {
		return username;
	}

	public PrivateKey getPrivateKey() {
		return privateKey;
	}

	public PKIAlgorithm getPKIAlgorithm() {
		return pkiAlgorithm;
	}

	public int getKeyNumber() {
		return keyNumber;
	}

	public long getUserId() {
		return userId;
	}

}
