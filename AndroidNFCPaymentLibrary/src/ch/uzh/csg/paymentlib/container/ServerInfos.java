package ch.uzh.csg.paymentlib.container;

import java.security.PublicKey;

import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;

//TODO: javadoc
public class ServerInfos {
	
	private PublicKey publicKey;
	
	public ServerInfos(PublicKey publicKey) throws IllegalArgumentException {
		if (publicKey == null)
			throw new IllegalArgumentException("The privatekey cannot be null.");
		
		this.publicKey = publicKey;
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}

}
