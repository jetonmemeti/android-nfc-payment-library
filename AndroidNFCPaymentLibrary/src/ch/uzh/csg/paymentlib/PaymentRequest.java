package ch.uzh.csg.paymentlib;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.ArrayList;

import ch.uzh.csg.nfclib.util.Utils;
import ch.uzh.csg.paymentlib.exceptions.NotSignedException;

//TODO: javadoc
public class PaymentRequest {
	private byte version;
	private byte signatureAlgorithm;
	
	private byte usernameSellerLength;
	private byte[] usernameSeller;
	private byte usernameBuyerLength;
	private byte[] usernameBuyer;
	
	private byte currency;
	private byte[] amount; //8 bytes (long)
	private byte[] timestamp; //8 bytes (long)
	private byte keyNumber;
	
	private byte[] payload; //calculated from the fields above
	private byte[] signature;
	
	public PaymentRequest(int version, SignatureAlgorithm signatureAlgorithm, String usernameSeller, String usernameBuyer, Currency currency, long amount, long timestamp, int keyNumber) throws IllegalArgumentException, UnsupportedEncodingException {
		checkParameters(version, signatureAlgorithm, usernameSeller, usernameBuyer, currency, amount, timestamp, keyNumber);
		
		this.version = (byte) version;
		this.signatureAlgorithm = signatureAlgorithm.getCode();
		
		this.usernameSeller = usernameSeller.getBytes("UTF-8");
		this.usernameSellerLength = (byte) this.usernameSeller.length;
		this.usernameBuyer = usernameBuyer.getBytes("UTF-8");
		this.usernameBuyerLength = (byte) this.usernameBuyer.length;
		
		this.currency = currency.getCode();
		this.amount = Utils.getLongAsBytes(amount);
		this.timestamp = Utils.getLongAsBytes(timestamp);
		this.keyNumber = (byte) keyNumber;
		
		setPayload();
	}
	
	private PaymentRequest(byte version, byte signatureAlgorithm, byte usernameSellerLength, byte[] usernameSeller, byte usernameBuyerLength, byte[] usernameBuyer, byte currency, byte[] amount, byte[] timestamp, byte keyNumber) {
		this.version = version;
		this.signatureAlgorithm = signatureAlgorithm;
		
		this.usernameSellerLength = usernameSellerLength;
		this.usernameSeller = usernameSeller;
		this.usernameBuyerLength = usernameBuyerLength;
		this.usernameBuyer = usernameBuyer;
		
		this.currency = currency;
		this.amount = amount; //8 bytes (long)
		this.timestamp = timestamp; //8 bytes (long)
		this.keyNumber = keyNumber;
		
		setPayload();
	}
	
	private void setPayload() {
		ArrayList<Byte> payload = new ArrayList<Byte>();
		payload.add(version);
		payload.add(signatureAlgorithm);
		payload.add(usernameSellerLength);
		for (int i=0; i<usernameSeller.length; i++) {
			payload.add(usernameSeller[i]);
		}
		payload.add(usernameBuyerLength);
		for (int i=0; i<usernameBuyer.length; i++) {
			payload.add(usernameBuyer[i]);
		}
		payload.add(currency);
		for (int i=0; i<amount.length; i++) {
			payload.add(amount[i]);
		}
		for (int i=0; i<timestamp.length; i++) {
			payload.add(timestamp[i]);
		}
		payload.add(keyNumber);
		
		byte[] bytes = new byte[payload.size()];
		for (int i=0; i<payload.size(); i++) {
			bytes[i] = payload.get(i);
		}
		
		this.payload = bytes;
	}
	
	private void checkParameters(int version, SignatureAlgorithm signatureAlgorithm, String usernameSeller, String usernameBuyer, Currency currency, long amount, long timestamp, int keyNumber) throws IllegalArgumentException {
		if (version <= 0 || version > 255)
			throw new IllegalArgumentException("The version number must be between 0 and 255.");
			
		if (signatureAlgorithm == null)
			throw new IllegalArgumentException("The signature algorithm cannot be null.");
		
		if (usernameSeller == null || usernameSeller.length() == 0 || usernameSeller.length() > 255)
			throw new IllegalArgumentException("The seller's username cannot be null, empty, or longer than 255 characters.");
		
		if (usernameBuyer == null || usernameBuyer.length() == 0 || usernameBuyer.length() > 255)
			throw new IllegalArgumentException("The buyer's username cannot be null, empty, or longer than 255 characters.");
		
		if (currency == null)
			throw new IllegalArgumentException("The currency cannot be null.");
		
		if (amount <= 0)
			throw new IllegalArgumentException("The amount must be greatern than 0.");
		
		if (timestamp <= 0)
			throw new IllegalArgumentException("The amount must be greatern than 0.");
		
		if (keyNumber <= 0 || keyNumber > 255)
			throw new IllegalArgumentException("The key number must be between 0 and 255.");
	}

	public void sign(PrivateKey privateKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		Signature sig = Signature.getInstance(SignatureAlgorithm.getSignatureAlgorithm(signatureAlgorithm).getDescription());
		sig.initSign(privateKey);
		sig.update(payload);
		signature = sig.sign();
	}

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
	
	public static PaymentRequest decode(byte[] bytes) throws IllegalArgumentException {
		if (bytes == null)
			throw new IllegalArgumentException("None of the arguments can be null.");
		
		try {
			int index = 0;
			
			byte version = bytes[index++];
			byte signatureAlgorithm = bytes[index++];
			
			byte usernameSellerLength = bytes[index++];
			int usLength = usernameSellerLength & 0xFF;
			byte[] usernameSeller = new byte[usLength];
			for (int i=0; i<usLength; i++) {
				usernameSeller[i] = bytes[index++];
			}
			
			byte usernameBuyerLength = bytes[index++];
			int ubLength = usernameBuyerLength & 0xFF;
			byte[] usernameBuyer = new byte[ubLength];
			for (int i=0; i<ubLength; i++) {
				usernameBuyer[i] = bytes[index++];
			}
			
			byte currency = bytes[index++];
			
			byte[] amount = new byte[Long.SIZE / Byte.SIZE]; //8 bytes (long)
			for (int i=0; i<amount.length; i++) {
				amount[i] = bytes[index++];
			}
			
			byte[] timestamp = new byte[Long.SIZE / Byte.SIZE]; //8 bytes (long)
			for (int i=0; i<timestamp.length; i++) {
				timestamp[i] = bytes[index++];
			}
			
			byte keyNumber = bytes[index++];
			
			byte[] signature = new byte[bytes.length - index];
			for (int i=0; i<signature.length; i++) {
				signature[i] = bytes[index++];
			}
			
			PaymentRequest pr = new PaymentRequest(version, signatureAlgorithm, usernameSellerLength, usernameSeller, usernameBuyerLength, usernameBuyer, currency, amount, timestamp, keyNumber);
			pr.signature = signature;
			
			return pr;
		} catch (IndexOutOfBoundsException e) {
			throw new IllegalArgumentException("The given byte array is corrupt (not long enough).");
		}
	}
	
	public boolean verify(PublicKey publicKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		Signature sig = Signature.getInstance(SignatureAlgorithm.getSignatureAlgorithm(signatureAlgorithm).getDescription());
		sig.initVerify(publicKey);
		sig.update(this.payload);
		return sig.verify(signature);
	}
	
}
