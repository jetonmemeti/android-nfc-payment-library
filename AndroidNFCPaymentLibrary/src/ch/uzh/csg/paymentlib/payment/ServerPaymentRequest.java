package ch.uzh.csg.paymentlib.payment;

import ch.uzh.csg.nfclib.util.Utils;
import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;
import ch.uzh.csg.paymentlib.exceptions.NotSignedException;
import ch.uzh.csg.paymentlib.exceptions.UnknownCurrencyException;
import ch.uzh.csg.paymentlib.exceptions.UnknownSignatureAlgorithmException;

//TODO: javadoc
public class ServerPaymentRequest extends SerializableObject {
	private static final int NOF_BYTES_FOR_PAYLOAD_LENGTH = 2; // 2 bytes for the payload length, up to 65536 bytes
	
	private byte nofSignatures;
	
	private PaymentRequest paymentRequestPayer;
	private PaymentRequest paymentRequestPayee;
	
	//this constructor is needed for the DecoderFactory
	protected ServerPaymentRequest() {
	}
	
	private ServerPaymentRequest(int version, PaymentRequest paymentRequestPayer) throws IllegalArgumentException {
		super(version);
		this.nofSignatures = 1;
		checkParameters(nofSignatures, paymentRequestPayer);
		this.paymentRequestPayer = paymentRequestPayer;
	}
	
	public ServerPaymentRequest(PaymentRequest paymentRequestPayer) throws IllegalArgumentException {
		this(1, paymentRequestPayer);
	}
	
	private ServerPaymentRequest(int version, PaymentRequest paymentRequestPayer, PaymentRequest paymentRequestPayee) throws IllegalArgumentException {
		super(version);
		this.nofSignatures = 2;
		
		checkParameters(nofSignatures, paymentRequestPayer, paymentRequestPayee);
		
		this.paymentRequestPayer = paymentRequestPayer;
		this.paymentRequestPayee = paymentRequestPayee;
	}
	
	public ServerPaymentRequest(PaymentRequest paymentRequestPayer, PaymentRequest paymentRequestPayee) throws IllegalArgumentException {
		this(1, paymentRequestPayer, paymentRequestPayee);
	}
	
	private void checkParameters(byte nofSignatures, PaymentRequest paymentRequestPayer) throws IllegalArgumentException {
		if (nofSignatures <= 0 || nofSignatures > 2)
			throw new IllegalArgumentException("The Server Payment Request can only handle 1 or 2 signatures.");
		
		checkPaymentRequest(paymentRequestPayer, "payer");
	}
	
	private void checkParameters(byte nofSignatures, PaymentRequest paymentRequestPayer, PaymentRequest paymentRequestPayee) throws IllegalArgumentException {
		checkParameters(nofSignatures, paymentRequestPayer);
		checkPaymentRequest(paymentRequestPayee, "payee");
		
		if (!paymentRequestPayee.requestsIdentic(paymentRequestPayer))
			throw new IllegalArgumentException("The tow payment requests must be identic.");
	}
	
	private void checkPaymentRequest(PaymentRequest paymentRequest, String role) throws IllegalArgumentException {
		if (paymentRequest == null)
			throw new IllegalArgumentException("The "+role+"'s Payment Request can't be null.");
		
		int maxPayloadLength = (int) Math.pow(2, NOF_BYTES_FOR_PAYLOAD_LENGTH*Byte.SIZE) - 1;
		byte[] payload = paymentRequest.getPayload();
		if (payload == null || payload.length == 0 || payload.length > maxPayloadLength)
			throw new IllegalArgumentException("The "+role+"'s payload can't be null, empty or longer than "+maxPayloadLength+" bytes.");
		
		byte[] signature = paymentRequest.getSignature();
		if (signature == null || signature.length == 0)
			throw new IllegalArgumentException("The "+role+"'s Payment Request is not signed.");
		
		if (signature.length > 255)
			throw new IllegalArgumentException("The "+role+"'s signature is too long. A signature algorithm with output longer than 255 bytes is not supported.");
	}
	
	public byte getNofSignatures() {
		return nofSignatures;
	}
	
	public PaymentRequest getPaymentRequestPayer() {
		return paymentRequestPayer;
	}
	
	public PaymentRequest getPaymentRequestPayee() {
		return paymentRequestPayee;
	}

	@Override
	public byte[] encode() throws NotSignedException {
		int outputLength;
		if (nofSignatures == 1) {
			/*
			 * version
			 * + nofSignatures
			 * + paymentRequestPayer.getPayload().length
			 * + paymentRequestPayer.getPayload()
			 * + paymentRequestPayer.getSignature().length
			 * + paymentRequestPayer.getSignature()
			 */
			outputLength = 1+1+NOF_BYTES_FOR_PAYLOAD_LENGTH+paymentRequestPayer.getPayload().length+1+paymentRequestPayer.getSignature().length;
		} else {
			/*
			 * version
			 * + nofSignatures
			 * + paymentRequestPayer.getPayload().length
			 * + paymentRequestPayer.getPayload()
			 * + paymentRequestPayer.getSignature().length
			 * + paymentRequestPayer.getSignature()
			 * + versionPayee
			 * + signatureAlgorithmPayee
			 * + keyNumberPayee
			 * + paymentRequestPayee.getSignature()
			 */
			outputLength = 1+1+NOF_BYTES_FOR_PAYLOAD_LENGTH+paymentRequestPayer.getPayload().length+1+paymentRequestPayer.getSignature().length+1+1+1+paymentRequestPayee.getSignature().length;
		}
		
		int index = 0;
		byte[] result = new byte[outputLength];
		
		result[index++] = (byte) getVersion();
		result[index++] = nofSignatures;
		
		byte[] paloadLengthBytes = Utils.getShortAsBytes((short) paymentRequestPayer.getPayload().length);
		for (byte b : paloadLengthBytes) {
			result[index++] = b;
		}
		for (byte b : paymentRequestPayer.getPayload()) {
			result[index++] = b;
		}
		result[index++] = (byte) paymentRequestPayer.getSignature().length;
		for (byte b : paymentRequestPayer.getSignature()) {
			result[index++] = b;
		}
		if (nofSignatures > 1) {
			result[index++] = (byte) paymentRequestPayee.getVersion();
			result[index++] = paymentRequestPayee.getSignatureAlgorithm().getCode();
			result[index++] = (byte) paymentRequestPayee.getKeyNumber();
			for (byte b : paymentRequestPayee.getSignature()) {
				result[index++] = b;
			}
		}
		return result;
	}

	@Override
	public ServerPaymentRequest decode(byte[] bytes) throws IllegalArgumentException, NotSignedException, UnknownSignatureAlgorithmException, UnknownCurrencyException {
		if (bytes == null)
			throw new IllegalArgumentException("The argument can't be null.");
		
		try {
			int index = 0;
			
			int version = (bytes[index++] & 0xFF);
			byte nofSignatures = bytes[index++];
			
			byte[] indicatedPayloadLength = new byte[NOF_BYTES_FOR_PAYLOAD_LENGTH];
			for (int i=0; i<NOF_BYTES_FOR_PAYLOAD_LENGTH; i++) {
				indicatedPayloadLength[i] = bytes[index++];
			}
			int paymentRequestPayerPayloadLength = (Utils.getBytesAsShort(indicatedPayloadLength) & 0xFF);
			byte[] paymentRequestPayerPayload = new byte[paymentRequestPayerPayloadLength];
			for (int i=0; i<paymentRequestPayerPayloadLength; i++) {
				paymentRequestPayerPayload[i] = bytes[index++];
			}
			int paymentRequestPayerSignatureLength = bytes[index++] & 0xFF;
			byte[] paymentRequestPayerSignature = new byte[paymentRequestPayerSignatureLength];
			for (int i=0; i<paymentRequestPayerSignatureLength; i++) {
				paymentRequestPayerSignature[i] = bytes[index++];
			}
			
			byte[] paymentRequestPayerBytes = new byte[paymentRequestPayerPayloadLength+paymentRequestPayerSignatureLength];
			int newIndex = 0;
			for (byte b : paymentRequestPayerPayload) {
				paymentRequestPayerBytes[newIndex++] = b;
			}
			for (byte b : paymentRequestPayerSignature) {
				paymentRequestPayerBytes[newIndex++] = b;
			}
			
			PaymentRequest paymentRequestPayer = DecoderFactory.decode(PaymentRequest.class, paymentRequestPayerBytes);
			
			if (nofSignatures == 1) {
				return new ServerPaymentRequest(version, paymentRequestPayer);
			} else if (nofSignatures == 2) {
				byte versionPayee = bytes[index++];
				byte signatureAlgorithmCodePayee = bytes[index++];
				byte keyNumberPayee = bytes[index++];
				
				byte[] paymentRequestPayeePayload = paymentRequestPayerPayload;
				paymentRequestPayeePayload[0] = versionPayee;
				paymentRequestPayeePayload[1] = signatureAlgorithmCodePayee;
				paymentRequestPayeePayload[2] = keyNumberPayee;
				
				byte[] paymentRequestPayeeSignature = new byte[bytes.length - index];
				for (int i=0; i<paymentRequestPayeeSignature.length; i++) {
					paymentRequestPayeeSignature[i] = bytes[index++];
				}
				
				byte[] paymentRequestPayeeBytes = new byte[paymentRequestPayeePayload.length + paymentRequestPayeeSignature.length];
				newIndex = 0;
				for (byte b : paymentRequestPayeePayload) {
					paymentRequestPayeeBytes[newIndex++] = b;
				}
				for (byte b : paymentRequestPayeeSignature) {
					paymentRequestPayeeBytes[newIndex++] = b;
				}
				PaymentRequest paymentRequestPayee = DecoderFactory.decode(PaymentRequest.class, paymentRequestPayeeBytes);
				return new ServerPaymentRequest(version, paymentRequestPayer, paymentRequestPayee);
			} else {
				throw new IllegalArgumentException("The given byte array is corrupt.");
			}
		} catch (IndexOutOfBoundsException e) {
			throw new IllegalArgumentException("The given byte array is corrupt (not long enough).");
		}
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (!(o instanceof ServerPaymentRequest))
			return false;
		
		ServerPaymentRequest spr = (ServerPaymentRequest) o;
		if (getVersion() != spr.getVersion())
			return false;
		if (this.nofSignatures != spr.nofSignatures)
			return false;
		if (!getPaymentRequestPayer().equals(spr.getPaymentRequestPayer()))
			return false;
		if (nofSignatures == 2) {
			if (!getPaymentRequestPayee().equals(spr.getPaymentRequestPayee()))
				return false;
		}
		
		return true;
	}
	
}
