package ch.uzh.csg.paymentlib.container;

import java.security.PrivateKey;
import java.util.Random;

import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;
import ch.uzh.csg.paymentlib.paymentrequest.SignatureAlgorithm;

//TODO: javadoc
public class PayerInfos {
	
	private String username;
	private PrivateKey privateKey;
	private String keyAlgorithm;
	private int keyNumber;
	private long userId;
	
	public PayerInfos(String username, PrivateKey privateKey, String keyAlgorithm, int keyNumber) throws IllegalArgumentException {
		checkParams(username, privateKey, keyAlgorithm, keyNumber);
		
		this.username = username;
		this.privateKey = privateKey;
		this.keyAlgorithm = keyAlgorithm;
		this.keyNumber = keyNumber;
		this.userId = new Random().nextLong();
	}
	
	private void checkParams(String username, PrivateKey privateKey, String keyAlgorithm, int keyNumber) throws IllegalArgumentException {
		if (username == null || username.isEmpty() || username.length() > 255)
			throw new IllegalArgumentException("The username cannot be null, empty, or longer than 255 characters.");
		
		if (privateKey == null)
			throw new IllegalArgumentException("The privatekey cannot be null.");
			
		if (keyAlgorithm == null || keyAlgorithm.isEmpty())
			throw new IllegalArgumentException("The key algorithm cannot be null or empty.");
		
		if (!SignatureAlgorithm.isKeyGenAlgorithmSupported(keyAlgorithm))
			throw new IllegalArgumentException("The key algorithm "+keyAlgorithm+" is not supported.");
		
		if (keyNumber <= 0 || keyNumber > 255)
			throw new IllegalArgumentException("The key number must be between 1 and 255.");
	}

	public String getUsername() {
		return username;
	}

	public PrivateKey getPrivateKey() {
		return privateKey;
	}

	public String getKeyAlgorithm() {
		return keyAlgorithm;
	}

	public int getKeyNumber() {
		return keyNumber;
	}

	public long getUserId() {
		return userId;
	}

}
