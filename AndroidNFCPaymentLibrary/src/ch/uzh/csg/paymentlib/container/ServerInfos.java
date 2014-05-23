package ch.uzh.csg.paymentlib.container;

import java.security.PublicKey;

import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;
import ch.uzh.csg.paymentlib.payment.SignatureAlgorithm;

//TODO: javadoc
public class ServerInfos {
	
	private PublicKey publicKey;
	private SignatureAlgorithm signatureAlgorithm;
	
	public ServerInfos(PublicKey publicKey, SignatureAlgorithm signatureAlgorithm) throws IllegalArgumentException {
		checkParameters(publicKey, signatureAlgorithm);
		
		this.publicKey = publicKey;
		this.signatureAlgorithm = signatureAlgorithm;
	}

	private void checkParameters(PublicKey publicKey, SignatureAlgorithm signatureAlgorithm) throws IllegalArgumentException {
		if (publicKey == null)
			throw new IllegalArgumentException("The privatekey cannot be null.");
			
		if (signatureAlgorithm == null)
			throw new IllegalArgumentException("The signature algorithm cannot be null or empty.");
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}

	public SignatureAlgorithm getSignatureAlgorithm() {
		return signatureAlgorithm;
	}
	
}
