package ch.uzh.csg.paymentlib.payment;

import java.io.UnsupportedEncodingException;

import ch.uzh.csg.nfclib.util.Utils;
import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;
import ch.uzh.csg.paymentlib.exceptions.NotSignedException;
import ch.uzh.csg.paymentlib.exceptions.UnknownCurrencyException;
import ch.uzh.csg.paymentlib.exceptions.UnknownSignatureAlgorithmException;

//TODO: javadoc
public class PaymentRequest extends SignedSerializableObject {
	
	private String usernamePayer;
	private String usernamePayee;
	
	private Currency currency;
	private long amount;
	private long timestamp;
	
	//this constructor is needed for the DecoderFactory
	protected PaymentRequest() {
	}

	public PaymentRequest(SignatureAlgorithm signatureAlgorithm, int keyNumber, String usernamePayer, String usernamePayee, Currency currency, long amount, long timestamp) throws IllegalArgumentException, UnsupportedEncodingException {
		this(1, signatureAlgorithm, keyNumber, usernamePayer, usernamePayee, currency, amount, timestamp);
	}
	
	private PaymentRequest(int version, SignatureAlgorithm signatureAlgorithm, int keyNumber, String usernamePayer, String usernamePayee, Currency currency, long amount, long timestamp) throws IllegalArgumentException, UnsupportedEncodingException {
		super(version, signatureAlgorithm, keyNumber);
		
		checkParameters(usernamePayer, usernamePayee, currency, amount, timestamp);
		
		this.usernamePayer = usernamePayer;
		this.usernamePayee = usernamePayee;
		this.currency = currency;
		this.amount = amount;
		this.timestamp = timestamp;
		
		setPayload();
	}

	private void checkParameters(String usernamePayer, String usernamePayee, Currency currency, long amount, long timestamp) throws IllegalArgumentException {
		if (usernamePayer == null || usernamePayer.length() == 0 || usernamePayer.length() > 255)
			throw new IllegalArgumentException("The payers's username cannot be null, empty, or longer than 255 characters.");
		
		if (usernamePayee == null || usernamePayee.length() == 0 || usernamePayee.length() > 255)
			throw new IllegalArgumentException("The payee's username cannot be null, empty, or longer than 255 characters.");
		
		if (usernamePayee.equalsIgnoreCase(usernamePayer))
			throw new IllegalArgumentException("The payee's username can't be equals to the payer's username.");
		
		if (currency == null)
			throw new IllegalArgumentException("The currency cannot be null.");
		
		if (amount <= 0)
			throw new IllegalArgumentException("The amount must be greatern than 0.");
		
		if (timestamp <= 0)
			throw new IllegalArgumentException("The timestamp must be greatern than 0.");
	}
	
	private void setPayload() throws UnsupportedEncodingException {
		byte[] usernamePayerBytes = usernamePayer.getBytes("UTF-8");
		byte[] usernamePayeeBytes = usernamePayee.getBytes("UTF-8");
		byte[] amountBytes = Utils.getLongAsBytes(amount);
		byte[] timestampBytes = Utils.getLongAsBytes(timestamp);
		
		/*
		 * version
		 * + signatureAlgorithm.getCode()
		 * + keyNumber
		 * + usernamePayer.length
		 * + usernamePayer
		 * + usernamePayee.length
		 * + usernamePayee
		 * + currency.getCode()
		 * + amount
		 * + timestamp
		 */
		int length = 1+1+1+usernamePayerBytes.length+1+usernamePayeeBytes.length+1+8+8+1;
		byte[] payload = new byte[length];
		
		int index = 0;
		
		payload[index++] = (byte) getVersion();
		payload[index++] = getSignatureAlgorithm().getCode();
		payload[index++] = (byte) getKeyNumber();
		payload[index++] = (byte) usernamePayerBytes.length;
		for (byte b : usernamePayerBytes) {
			payload[index++] = b;
		}
		payload[index++] = (byte) usernamePayeeBytes.length;
		for (byte b : usernamePayeeBytes) {
			payload[index++] = b;
		}
		payload[index++] = currency.getCode();
		for (byte b : amountBytes) {
			payload[index++] = b;
		}
		for (byte b : timestampBytes) {
			payload[index++] = b;
		}
		
		this.payload = payload;
	}
	
	public String getUsernamePayer() {
		return usernamePayer;
	}

	public String getUsernamePayee() {
		return usernamePayee;
	}

	public Currency getCurrency() {
		return currency;
	}

	public long getAmount() {
		return amount;
	}

	public long getTimestamp() {
		return timestamp;
	}

	@Override
	public PaymentRequest decode(byte[] bytes) throws IllegalArgumentException, NotSignedException, UnknownSignatureAlgorithmException, UnknownCurrencyException {
		if (bytes == null)
			throw new IllegalArgumentException("The argument can't be null.");
		
		try {
			int index = 0;
			
			int version = bytes[index++] & 0xFF;
			SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.getSignatureAlgorithm(bytes[index++]);
			int keyNumber = bytes[index++] & 0xFF;
			
			int usernamePayerLength = bytes[index++] & 0xFF;
			byte[] usernamePayerBytes = new byte[usernamePayerLength];
			for (int i=0; i<usernamePayerLength; i++) {
				usernamePayerBytes[i] = bytes[index++];
			}
			String usernamePayer = new String(usernamePayerBytes);
			
			int usernamePayeeLength = bytes[index++] & 0xFF;
			byte[] usernamePayeeBytes = new byte[usernamePayeeLength];
			for (int i=0; i<usernamePayeeLength; i++) {
				usernamePayeeBytes[i] = bytes[index++];
			}
			String usernamePayee = new String(usernamePayeeBytes);
			
			Currency currency = Currency.getCurrency(bytes[index++]);
			
			byte[] amountBytes = new byte[Long.SIZE / Byte.SIZE]; //8 bytes (long)
			for (int i=0; i<amountBytes.length; i++) {
				amountBytes[i] = bytes[index++];
			}
			long amount = Utils.getBytesAsLong(amountBytes);
			
			byte[] timestampBytes = new byte[Long.SIZE / Byte.SIZE]; //8 bytes (long)
			for (int i=0; i<timestampBytes.length; i++) {
				timestampBytes[i] = bytes[index++];
			}
			long timestamp = Utils.getBytesAsLong(timestampBytes);
			
			PaymentRequest pr = new PaymentRequest(version, signatureAlgorithm, keyNumber, usernamePayer, usernamePayee, currency, amount, timestamp);
			
			int signatureLength = bytes.length - index;
			if (signatureLength == 0) {
				throw new NotSignedException();
			} else {
				byte[] signature = new byte[signatureLength];
				for (int i=0; i<signature.length; i++) {
					signature[i] = bytes[index++];
				}
				pr.signature = signature;
			}
			
			return pr;
		} catch (IndexOutOfBoundsException e) {
			throw new IllegalArgumentException("The given byte array is corrupt (not long enough).");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalArgumentException("The given byte array is corrupt.");
		}
	}
	
	/**
	 * This method checks that two payment requests are identic regarding a
	 * payment. The username of payer and payee as well as the currency and the
	 * amount must be equals in order to return true.
	 */
	public boolean requestsIdentic(PaymentRequest pr) {
		if (pr == null)
			return false;
		
		if (!this.usernamePayer.equals(pr.usernamePayer))
			return false;
		if (!this.usernamePayee.equals(pr.usernamePayee))
			return false;
		if (this.currency.getCode() != pr.currency.getCode())
			return false;
		if (this.amount != pr.amount)
			return false;
		
		return true;
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (!(o instanceof PaymentRequest))
			return false;
		
		PaymentRequest pr = (PaymentRequest) o;
		if (getVersion() != pr.getVersion())
			return false;
		if (getSignatureAlgorithm().getCode() != pr.getSignatureAlgorithm().getCode())
			return false;
		if (getKeyNumber() != pr.getKeyNumber())
			return false;
		if (!this.usernamePayer.equals(pr.usernamePayer))
			return false;
		if (!this.usernamePayee.equals(pr.usernamePayee))
			return false;
		if (this.currency.getCode() != pr.currency.getCode())
			return false;
		if (this.amount != pr.amount)
			return false;
		if (this.timestamp != pr.timestamp)
			return false;
		
		return true;
	}
	
}
