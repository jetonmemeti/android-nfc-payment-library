package ch.uzh.csg.paymentlib.payment;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;

import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;
import ch.uzh.csg.paymentlib.exceptions.NotSignedException;

//TODO: javadoc
public abstract class SignedSerializableObject extends SerializableObject {
	
	private SignatureAlgorithm signatureAlgorithm;
	private int keyNumber;
	
	/*
	 * payload and signature are not serialized but only hold references in
	 * order to save cpu time
	 */
	protected byte[] payload;
	protected byte[] signature;
	
	//this constructor is needed for the DecoderFactory
	protected SignedSerializableObject() {
	}

	public SignedSerializableObject(int version, SignatureAlgorithm signatureAlgorithm, int keyNumber) throws IllegalArgumentException {
		super(version);
		
		if (signatureAlgorithm == null)
			throw new IllegalArgumentException("The signature algorithm cannot be null.");
		
		if (keyNumber <= 0 || keyNumber > 255)
			throw new IllegalArgumentException("The key number must be between 1 and 255.");
		
		this.signatureAlgorithm = signatureAlgorithm;
		this.keyNumber = keyNumber;
	}
	
	public SignatureAlgorithm getSignatureAlgorithm() {
		return signatureAlgorithm;
	}
	
	public int getKeyNumber() {
		return keyNumber;
	}
	
	public byte[] getPayload() {
		return payload;
	}
	
	public byte[] getSignature() {
		return signature;
	}
	
	public void sign(PrivateKey privateKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		Signature sig = Signature.getInstance(signatureAlgorithm.getSignatureAlgorithm());
		sig.initSign(privateKey);
		sig.update(payload);
		signature = sig.sign();
	}
	
	public boolean verify(PublicKey publicKey) throws NotSignedException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		if (signature == null)
			throw new NotSignedException();
		
		Signature sig = Signature.getInstance(signatureAlgorithm.getSignatureAlgorithm());
		sig.initVerify(publicKey);
		sig.update(payload);
		return sig.verify(signature);
	}
	
	/**
	 * Returns the raw payload of this object and attaches the raw
	 * signature to it.
	 * 
	 * @throws NotSignedException
	 *             if the object was not signed before
	 */
	@Override
	public byte[] encode() throws NotSignedException {
		if (signature == null)
			throw new NotSignedException();
		
		int index = 0;
		byte[] result = new byte[payload.length+signature.length];
		for (byte b : payload) {
			result[index++] = b;
		}
		for (byte b : signature) {
			result[index++] = b;
		}
		
		return result;
	}

}
