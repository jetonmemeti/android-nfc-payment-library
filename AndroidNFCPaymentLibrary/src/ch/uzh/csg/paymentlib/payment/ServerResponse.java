package ch.uzh.csg.paymentlib.payment;

import java.io.UnsupportedEncodingException;

import ch.uzh.csg.nfclib.util.Utils;
import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;
import ch.uzh.csg.paymentlib.exceptions.NotSignedException;
import ch.uzh.csg.paymentlib.exceptions.UnknownCurrencyException;
import ch.uzh.csg.paymentlib.exceptions.UnknownSignatureAlgorithmException;

//TODO: javadoc
public class ServerResponse extends SignedSerializableObject {
	private static final int NOF_BYTES_FOR_PAYMENT_REQUEST_LENGTH = 2; // 2 bytes for the payload length, up to 65536 bytes

	private PaymentRequest paymentRequest;
	private ServerResponseStatus status;
	private String reason;
	
	//this constructor is needed for the DecoderFactory
	protected ServerResponse() {
	}
	
	public ServerResponse(SignatureAlgorithm signatureAlgorithm, PaymentRequest paymentRequest, ServerResponseStatus status, String reason) throws IllegalArgumentException, UnsupportedEncodingException {
		this(1, signatureAlgorithm, paymentRequest, status, reason);
	}
	
	private ServerResponse(int version, SignatureAlgorithm signatureAlgorithm, PaymentRequest paymentRequest, ServerResponseStatus status, String reason) throws IllegalArgumentException, UnsupportedEncodingException {
		super(1, signatureAlgorithm);
		checkParameters(paymentRequest, status, reason);
		
		this.paymentRequest = paymentRequest;
		this.status = status;
		this.reason = reason;
		
		setPayload();
	}
	
	private static void checkParameters(PaymentRequest paymentRequest, ServerResponseStatus status, String reason) throws IllegalArgumentException {
		if (paymentRequest == null)
			throw new IllegalArgumentException("The payment request cannot be null.");
		
		int maxPayloadLength = (int) Math.pow(2, NOF_BYTES_FOR_PAYMENT_REQUEST_LENGTH*Byte.SIZE) - 1;
		byte[] payload = paymentRequest.getPayload();
		if (payload == null || payload.length == 0 || payload.length > maxPayloadLength)
			throw new IllegalArgumentException("The payment requests's payload can't be null, empty or longer than "+maxPayloadLength+" bytes.");
		
		if (status == null)
			throw new IllegalArgumentException("The status cannot be null.");
		
		if (status.getCode() == 2) {
			if (reason == null)
				throw new IllegalArgumentException("The reason cannot be null if the status is set to FAILURE.");
			if (reason.length() > 255)
				throw new IllegalArgumentException("The reason cannot be longer than 255 characters.");
		}
	}
	
	private void setPayload() throws UnsupportedEncodingException {
		/*
		 * version
		 * + signatureAlgorithm
		 * + paymentRequest.length
		 * + paymentRequest
		 * + status
		 * + reason.length
		 * + reason
		 */
		byte[] reasonBytes = null;
		int length;
		if (status == ServerResponseStatus.SUCCESS) {
			length = 1+1+NOF_BYTES_FOR_PAYMENT_REQUEST_LENGTH+paymentRequest.getPayload().length+1;
		} else {
			reasonBytes = reason.getBytes("UTF-8");
			length = 1+1+NOF_BYTES_FOR_PAYMENT_REQUEST_LENGTH+paymentRequest.getPayload().length+1+1+reasonBytes.length;
		}
		
		byte[] payload = new byte[length];
		
		int index = 0;
		
		payload[index++] = (byte) getVersion();
		payload[index++] = getSignatureAlgorithm().getCode();
		
		byte[] paymentRequestLengthBytes = Utils.getShortAsBytes((short) paymentRequest.getPayload().length);
		for (byte b : paymentRequestLengthBytes) {
			payload[index++] = b;
		}
		for (byte b : paymentRequest.getPayload()) {
			payload[index++] = b;
		}
		
		payload[index++] = status.getCode();
		
		if (status == ServerResponseStatus.FAILURE) {
			payload[index++] = (byte) reason.length();
			for (byte b : reasonBytes) {
				payload[index++] = b;
			}
		}
		
		this.payload = payload;
	}
	
	public PaymentRequest getPaymentRequest() {
		return paymentRequest;
	}
	
	public ServerResponseStatus getStatus() {
		return status;
	}
	
	public String getReason() {
		return reason;
	}
	
	@Override
	public ServerResponse decode(byte[] bytes) throws IllegalArgumentException, NotSignedException, UnknownSignatureAlgorithmException, UnknownCurrencyException {
		if (bytes == null)
			throw new IllegalArgumentException("The argument can't be null.");
		
		try {
			int index = 0;
			
			int version = bytes[index++];
			SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.getSignatureAlgorithm(bytes[index++]);
			
			byte[] paymentRequestLengthBytes = new byte[NOF_BYTES_FOR_PAYMENT_REQUEST_LENGTH];
			for (int i=0; i<NOF_BYTES_FOR_PAYMENT_REQUEST_LENGTH; i++) {
				paymentRequestLengthBytes[i] = bytes[index++];
			}
			int paymentRequestLength = Utils.getBytesAsShort(paymentRequestLengthBytes) & 0xFF;
			
			byte[] paymentRequestBytes = new byte[paymentRequestLength];
			for (int i=0; i<paymentRequestLength; i++) {
				paymentRequestBytes[i] = bytes[index++];
			}
			PaymentRequest paymentRequest = DecoderFactory.decode(PaymentRequest.class, paymentRequestBytes);

			ServerResponseStatus status = ServerResponseStatus.getStatus(bytes[index++]);
			
			String reason;
			if (status == ServerResponseStatus.FAILURE) {
				int reasonLength = bytes[index++] & 0xFF;
				byte[] reasonBytes = new byte[reasonLength];
				for (int i=0; i<reasonLength; i++) {
					reasonBytes[i] = bytes[index++];
				}
				reason = new String(reasonBytes);
			} else {
				reason = null;
			}
			
			ServerResponse sr = new ServerResponse(version, signatureAlgorithm, paymentRequest, status, reason);
			
			int signatureLength = bytes.length - index;
			if (signatureLength == 0) {
				throw new NotSignedException();
			} else {
				byte[] signature = new byte[signatureLength];
				for (int i=0; i<signature.length; i++) {
					signature[i] = bytes[index++];
				}
				sr.signature = signature;
			}
			
			return sr;
		} catch (IndexOutOfBoundsException e) {
			throw new IllegalArgumentException("The given byte array is corrupt (not long enough).");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalArgumentException("The given byte array is corrupt.");
		}
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (!(o instanceof ServerResponse))
			return false;
		
		ServerResponse sr = (ServerResponse) o;
		if (getVersion() != sr.getVersion())
			return false;
		if (getSignatureAlgorithm().getCode() != sr.getSignatureAlgorithm().getCode())
			return false;
		if (!this.paymentRequest.equals(sr.paymentRequest))
			return false;
		if (this.status.getCode() != sr.status.getCode())
			return false;
		if (this.reason == null && sr.reason != null)
			return false;
		if (this.reason != null && sr.reason == null)
			return false;
		if (this.reason == null && sr.reason == null)
			return true;
		if (!this.reason.equals(sr.reason))
			return false;
		
		return true;
	}

}
