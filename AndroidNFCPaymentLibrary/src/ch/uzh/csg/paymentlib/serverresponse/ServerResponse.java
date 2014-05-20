package ch.uzh.csg.paymentlib.serverresponse;

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
import ch.uzh.csg.paymentlib.paymentrequest.PaymentRequest;
import ch.uzh.csg.paymentlib.paymentrequest.SignatureAlgorithm;

//TODO: javadoc
public class ServerResponse {
	private static final int NOF_BYTES_FOR_PAYMENT_REQUEST_LENGTH = 2; // 2 bytes for the payload length, up to 65536 bytes

	private int version;
	private SignatureAlgorithm signatureAlgorithm;
	private PaymentRequest paymentRequest;
	private ServerResponseStatus status;
	private String reason;
	
	/*
	 * payload and signature are not serialized but only hold references in
	 * order to save cpu time
	 */
	private byte[] payload;
	private byte[] signature;
	
	private ServerResponse() {
	}
	
	public ServerResponse(SignatureAlgorithm signatureAlgorithm, PaymentRequest paymentRequest, ServerResponseStatus status, String reason) throws IllegalArgumentException, UnsupportedEncodingException {
		checkParameters(signatureAlgorithm, paymentRequest, status, reason);
		
		this.version = 1;
		this.signatureAlgorithm = signatureAlgorithm;
		this.paymentRequest = paymentRequest;
		this.status = status;
		this.reason = reason;
		
		setPayload();
	}
	
	private static void checkParameters(SignatureAlgorithm signatureAlgorithm, PaymentRequest paymentRequest, ServerResponseStatus status, String reason) throws IllegalArgumentException {
		if (signatureAlgorithm == null)
			throw new IllegalArgumentException("The signature algorithm cannot be null.");
		
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
		
		payload[index++] = (byte) version;
		payload[index++] = signatureAlgorithm.getCode();
		
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
	
	public static ServerResponse decode(byte[] bytes) throws IllegalArgumentException {
		if (bytes == null)
			throw new IllegalArgumentException("The argument can't be null.");
		
		try {
			int index = 0;
			
			ServerResponse sr = new ServerResponse();
			sr.version = bytes[index++] & 0xFF;
			sr.signatureAlgorithm = SignatureAlgorithm.getSignatureAlgorithm(bytes[index++]);
			
			byte[] paymentRequestLengthBytes = new byte[NOF_BYTES_FOR_PAYMENT_REQUEST_LENGTH];
			for (int i=0; i<NOF_BYTES_FOR_PAYMENT_REQUEST_LENGTH; i++) {
				paymentRequestLengthBytes[i] = bytes[index++];
			}
			int paymentRequestLength = Utils.getBytesAsShort(paymentRequestLengthBytes) & 0xFF;
			
			byte[] paymentRequest = new byte[paymentRequestLength];
			for (int i=0; i<paymentRequestLength; i++) {
				paymentRequest[i] = bytes[index++];
			}
			sr.paymentRequest = PaymentRequest.decode(paymentRequest);
			
			sr.status = ServerResponseStatus.getStatus(bytes[index++]);
			
			if (sr.status == ServerResponseStatus.FAILURE) {
				int reasonLength = bytes[index++] & 0xFF;
				byte[] reasonBytes = new byte[reasonLength];
				for (int i=0; i<reasonLength; i++) {
					reasonBytes[i] = bytes[index++];
				}
				sr.reason = new String(reasonBytes);
			} else {
				sr.reason = null;
			}
			
			sr.setPayload();
			checkParameters(sr.signatureAlgorithm, sr.paymentRequest, sr.status, sr.reason);
			
			byte[] signature = new byte[bytes.length - index];
			for (int i=0; i<signature.length; i++) {
				signature[i] = bytes[index++];
			}
			sr.signature = signature;
			
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
		if (this.version != sr.version)
			return false;
		if (this.signatureAlgorithm.getCode() != sr.signatureAlgorithm.getCode())
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
