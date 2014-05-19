package ch.uzh.csg.paymentlib.paymentrequest;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;

import ch.uzh.csg.nfclib.util.Utils;
import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;
import ch.uzh.csg.paymentlib.exceptions.NotSignedException;

//TODO: javadoc
public class PaymentRequest {
	private int version;
	private SignatureAlgorithm signatureAlgorithm;
	
	private String usernameBuyer;
	private String usernameSeller;
	
	private Currency currency;
	private long amount;
	private long timestamp;
	private int keyNumber;
	
	/*
	 * payload and signature are not serialized but only hold references in
	 * order to save cpu time
	 */
	private byte[] payload;
	private byte[] signature;
	
	private PaymentRequest() {
	}
	
	public PaymentRequest(SignatureAlgorithm signatureAlgorithm, String usernameBuyer, String usernameSeller, Currency currency, long amount, long timestamp, int keyNumber) throws IllegalArgumentException, UnsupportedEncodingException {
		this(1, signatureAlgorithm, usernameBuyer, usernameSeller, currency, amount, timestamp, keyNumber);
	}
	
	protected PaymentRequest(int version, SignatureAlgorithm signatureAlgorithm, String usernameBuyer, String usernameSeller, Currency currency, long amount, long timestamp, int keyNumber) throws IllegalArgumentException, UnsupportedEncodingException {
		checkParameters(version, signatureAlgorithm, usernameBuyer, usernameSeller, currency, amount, timestamp, keyNumber);
		
		this.version = version;
		this.signatureAlgorithm = signatureAlgorithm;
		this.usernameBuyer = usernameBuyer;
		this.usernameSeller = usernameSeller;
		this.currency = currency;
		this.amount = amount;
		this.timestamp = timestamp;
		this.keyNumber = keyNumber;
		
		setPayload();
	}

	private void checkParameters(int version, SignatureAlgorithm signatureAlgorithm, String usernameBuyer, String usernameSeller, Currency currency, long amount, long timestamp, int keyNumber) throws IllegalArgumentException {
		if (version <= 0 || version > 255)
			throw new IllegalArgumentException("The version number must be between 0 and 255.");
		
		if (signatureAlgorithm == null)
			throw new IllegalArgumentException("The signature algorithm cannot be null.");
		
		if (usernameBuyer == null || usernameBuyer.length() == 0 || usernameBuyer.length() > 255)
			throw new IllegalArgumentException("The buyer's username cannot be null, empty, or longer than 255 characters.");
		
		if (usernameSeller == null || usernameSeller.length() == 0 || usernameSeller.length() > 255)
			throw new IllegalArgumentException("The seller's username cannot be null, empty, or longer than 255 characters.");
		
		if (currency == null)
			throw new IllegalArgumentException("The currency cannot be null.");
		
		if (amount <= 0)
			throw new IllegalArgumentException("The amount must be greatern than 0.");
		
		if (timestamp <= 0)
			throw new IllegalArgumentException("The timestamp must be greatern than 0.");
		
		if (keyNumber <= 0 || keyNumber > 255)
			throw new IllegalArgumentException("The key number must be between 1 and 255.");
	}
	
	private void setPayload() throws UnsupportedEncodingException {
		byte[] usernameBuyerBytes = usernameBuyer.getBytes("UTF-8");
		byte[] usernameSellerBytes = usernameSeller.getBytes("UTF-8");
		byte[] amountBytes = Utils.getLongAsBytes(amount);
		byte[] timestampBytes = Utils.getLongAsBytes(timestamp);
		
		int length = 1+1+1+usernameSellerBytes.length+1+usernameBuyerBytes.length+1+8+8+1;
		byte[] payload = new byte[length];
		
		int index = 0;
		
		payload[index++] = (byte) version;
		payload[index++] = signatureAlgorithm.getCode();
		payload[index++] = (byte) usernameBuyerBytes.length;
		for (byte b : usernameBuyerBytes) {
			payload[index++] = b;
		}
		payload[index++] = (byte) usernameSellerBytes.length;
		for (byte b : usernameSellerBytes) {
			payload[index++] = b;
		}
		payload[index++] = currency.getCode();
		for (byte b : amountBytes) {
			payload[index++] = b;
		}
		for (byte b : timestampBytes) {
			payload[index++] = b;
		}
		payload[index++] = (byte) keyNumber;
		
		this.payload = payload;
	}
	
	protected int getVersion() {
		return version;
	}

	protected SignatureAlgorithm getSignatureAlgorithm() {
		return signatureAlgorithm;
	}

	protected String getUsernameBuyer() {
		return usernameBuyer;
	}

	protected String getUsernameSeller() {
		return usernameSeller;
	}

	protected Currency getCurrency() {
		return currency;
	}

	protected long getAmount() {
		return amount;
	}

	protected long getTimestamp() {
		return timestamp;
	}

	protected int getKeyNumber() {
		return keyNumber;
	}
	
	protected byte[] getPayload() {
		return payload;
	}
	
	protected byte[] getSignature() throws NotSignedException {
		if (signature == null)
			throw new NotSignedException();
		
		return signature;
	}
	
	public void sign(PrivateKey privateKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, UnsupportedEncodingException {
		Signature sig = Signature.getInstance(signatureAlgorithm.getSignatureAlgorithm());
		sig.initSign(privateKey);
		sig.update(payload);
		signature = sig.sign();
	}
	
	public boolean verify(PublicKey publicKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, NotSignedException {
		if (signature == null)
			throw new NotSignedException();
		
		Signature sig = Signature.getInstance(signatureAlgorithm.getSignatureAlgorithm());
		sig.initVerify(publicKey);
		sig.update(this.payload);
		return sig.verify(signature);
	}
	
	/**
	 * Returns the raw payload of this PaymentRequest and attaches the raw
	 * signature to it.
	 * 
	 * @throws NotSignedException
	 *             if the PaymentRequest was not signed before
	 */
	public byte[] encode() throws NotSignedException {
		if (signature == null)
			throw new NotSignedException();
		
		int index = 0;
		byte[] result = new byte[payload.length+signature.length];
		for (byte b : payload) {
			result[index] = b;
			index++;
		}
		for (byte b : signature) {
			result[index] = b;
			index++;
		}
		
		return result;
	}

	/**
	 * Returns a PaymentRequest (payload and signature) based on the given
	 * bytes.
	 * 
	 * @param bytes
	 *            the raw data
	 * @throws IllegalArgumentException
	 *             if bytes is null or does not contain enough information to
	 *             create a PaymentRequest.
	 */
	public static PaymentRequest decode(byte[] bytes) throws IllegalArgumentException {
		if (bytes == null)
			throw new IllegalArgumentException("The argument can't be null.");
		
		try {
			int index = 0;
			
			PaymentRequest pr = new PaymentRequest();
			pr.version = bytes[index++] & 0xFF;
			pr.signatureAlgorithm = SignatureAlgorithm.getSignatureAlgorithm(bytes[index++]);
			
			int usernameBuyerLength = bytes[index++] & 0xFF;
			byte[] usernameBuyerBytes = new byte[usernameBuyerLength];
			for (int i=0; i<usernameBuyerLength; i++) {
				usernameBuyerBytes[i] = bytes[index++];
			}
			pr.usernameBuyer = new String(usernameBuyerBytes);
			
			int usernameSellerLength = bytes[index++] & 0xFF;
			byte[] usernameSellerBytes = new byte[usernameSellerLength];
			for (int i=0; i<usernameSellerLength; i++) {
				usernameSellerBytes[i] = bytes[index++];
			}
			pr.usernameSeller = new String(usernameSellerBytes);
			
			pr.currency = Currency.getCurrency(bytes[index++]);
			
			byte[] amountBytes = new byte[Long.SIZE / Byte.SIZE]; //8 bytes (long)
			for (int i=0; i<amountBytes.length; i++) {
				amountBytes[i] = bytes[index++];
			}
			pr.amount = Utils.getBytesAsLong(amountBytes);
			
			byte[] timestampBytes = new byte[Long.SIZE / Byte.SIZE]; //8 bytes (long)
			for (int i=0; i<timestampBytes.length; i++) {
				timestampBytes[i] = bytes[index++];
			}
			pr.timestamp = Utils.getBytesAsLong(timestampBytes);
			
			pr.keyNumber = bytes[index++] & 0xFF;
			
			pr.setPayload();
			
			byte[] signature = new byte[bytes.length - index];
			for (int i=0; i<signature.length; i++) {
				signature[i] = bytes[index++];
			}
			
			pr.signature = signature;
			
			return pr;
		} catch (IndexOutOfBoundsException e) {
			throw new IllegalArgumentException("The given byte array is corrupt (not long enough).");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalArgumentException("The given byte array is corrupt.");
		}
	}
	
}
