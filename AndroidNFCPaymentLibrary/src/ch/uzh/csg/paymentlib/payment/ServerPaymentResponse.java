package ch.uzh.csg.paymentlib.payment;

import ch.uzh.csg.nfclib.util.Utils;
import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;
import ch.uzh.csg.paymentlib.exceptions.NotSignedException;
import ch.uzh.csg.paymentlib.exceptions.UnknownCurrencyException;
import ch.uzh.csg.paymentlib.exceptions.UnknownSignatureAlgorithmException;

//TODO: javadoc
public class ServerPaymentResponse extends SerializableObject {
	private static final int NOF_BYTES_FOR_PAYMENT_RESPONSE_LENGTH = 2; // 2 bytes for the payload length, up to 65536 bytes
	
	private byte nofPaymentResponses;
	
	private PaymentResponse paymentResponsePayer;
	private PaymentResponse paymentResponsePayee;
	
	//this constructor is needed for the DecoderFactory
	protected ServerPaymentResponse() {
	}

	public ServerPaymentResponse(PaymentResponse paymentResponsePayer) throws IllegalArgumentException {
		this(1, paymentResponsePayer);
	}
	
	public ServerPaymentResponse(PaymentResponse paymentResponsePayer, PaymentResponse paymentResponsePayee) throws IllegalArgumentException {
		this(1, paymentResponsePayer, paymentResponsePayee);
	}
	
	private ServerPaymentResponse(int version, PaymentResponse paymentResponsePayer) throws IllegalArgumentException {
		super(version);
		checkParameters(paymentResponsePayer, "payer");
		this.paymentResponsePayer = paymentResponsePayer;
		this.nofPaymentResponses = 1;
	}
	
	private ServerPaymentResponse(int version, PaymentResponse paymentResponsePayer, PaymentResponse paymentResponsePayee) throws IllegalArgumentException {
		super(version);
		
		checkParameters(paymentResponsePayer, "payer");
		checkParameters(paymentResponsePayee, "payee");
		
		this.paymentResponsePayer = paymentResponsePayer;
		this.paymentResponsePayee = paymentResponsePayee;
		this.nofPaymentResponses = 2;
	}
	
	private static void checkParameters(PaymentResponse paymentResponse, String role) throws IllegalArgumentException {
		if (paymentResponse == null)
			throw new IllegalArgumentException("The payment response cannot be null.");
		
		int maxPayloadLength = (int) Math.pow(2, NOF_BYTES_FOR_PAYMENT_RESPONSE_LENGTH*Byte.SIZE) - 1;
		byte[] payload = paymentResponse.getPayload();
		if (payload == null || payload.length == 0 || payload.length > maxPayloadLength)
			throw new IllegalArgumentException("The "+role+"'s payment response payload can't be null, empty or longer than "+maxPayloadLength+" bytes.");
		
		byte[] signature = paymentResponse.getSignature();
		if (signature == null || signature.length == 0)
			throw new IllegalArgumentException("The "+role+"'s payment response is not signed.");
		
		if (signature.length > 255)
			throw new IllegalArgumentException("The "+role+"'s payment response signature is too long. A signature algorithm with output longer than 255 bytes is not supported.");
	}
	
	public PaymentResponse getPaymentResponsePayer() {
		return paymentResponsePayer;
	}

	public PaymentResponse getPaymentResponsePayee() {
		return paymentResponsePayee;
	}

	@Override
	public byte[] encode() throws NotSignedException {
		byte[] paymentResponsePayerRaw = null;
		byte[] paymentResponsePayeeRaw = null;
		
		int length;
		if (nofPaymentResponses == 1) {
			paymentResponsePayerRaw = paymentResponsePayer.encode();
			/*
			 * version
			 * + nofPaymentResponses
			 * + paymentResponsePayer.length
			 * + paymentResponsePayer
			 */
			length = 1+1+NOF_BYTES_FOR_PAYMENT_RESPONSE_LENGTH+paymentResponsePayerRaw.length;
		} else {
			paymentResponsePayerRaw = paymentResponsePayer.encode();
			paymentResponsePayeeRaw = paymentResponsePayee.encode();
			/*
			 * version
			 * + nofPaymentResponses
			 * + paymentResponsePayer.length
			 * + paymentResponsePayer
			 * + paymentResponsePayee.length
			 * + paymentResponsePayee
			 */
			length = 1+1+NOF_BYTES_FOR_PAYMENT_RESPONSE_LENGTH+paymentResponsePayerRaw.length+NOF_BYTES_FOR_PAYMENT_RESPONSE_LENGTH+paymentResponsePayeeRaw.length;
		}
		
		int index = 0;
		byte[] result = new byte[length];
		
		result[index++] = (byte) getVersion();
		result[index++] = nofPaymentResponses;
		
		byte[] paymentResponsePayerLengthBytes = Utils.getShortAsBytes((short) paymentResponsePayerRaw.length);
		for (byte b : paymentResponsePayerLengthBytes) {
			result[index++] = b;
		}
		for (byte b : paymentResponsePayerRaw) {
			result[index++] = b;
		}
		
		if (nofPaymentResponses > 1) {
			byte[] paymentResponsePayeeLengthBytes = Utils.getShortAsBytes((short) paymentResponsePayeeRaw.length);
			for (byte b : paymentResponsePayeeLengthBytes) {
				result[index++] = b;
			}
			for (byte b : paymentResponsePayeeRaw) {
				result[index++] = b;
			}
		}
		
		return result;
	}
	
	@Override
	public ServerPaymentResponse decode(byte[] bytes) throws IllegalArgumentException, NotSignedException, UnknownSignatureAlgorithmException, UnknownCurrencyException {
		if (bytes == null)
			throw new IllegalArgumentException("The argument can't be null.");
		
		try {
			int index = 0;
			
			int version = (bytes[index++] & 0xFF);
			byte nofPaymentResponses = bytes[index++];
			
			byte[] indicatedPaymentResponsePayerLengthBytes = new byte[NOF_BYTES_FOR_PAYMENT_RESPONSE_LENGTH];
			for (int i=0; i<NOF_BYTES_FOR_PAYMENT_RESPONSE_LENGTH; i++) {
				indicatedPaymentResponsePayerLengthBytes[i] = bytes[index++];
			}
			
			int paymentResponsePayerLength = (Utils.getBytesAsShort(indicatedPaymentResponsePayerLengthBytes) & 0xFF);
			byte[] paymentResponsePayerBytes = new byte[paymentResponsePayerLength];
			for (int i=0; i<paymentResponsePayerLength; i++) {
				paymentResponsePayerBytes[i] = bytes[index++];
			}
			
			PaymentResponse paymentResponsePayer = DecoderFactory.decode(PaymentResponse.class, paymentResponsePayerBytes);
			if (nofPaymentResponses == 1) {
				return new ServerPaymentResponse(version, paymentResponsePayer);
			} else if (nofPaymentResponses == 2) {
				byte[] indicatedPaymentResponsePayeeLengthBytes = new byte[NOF_BYTES_FOR_PAYMENT_RESPONSE_LENGTH];
				for (int i=0; i<NOF_BYTES_FOR_PAYMENT_RESPONSE_LENGTH; i++) {
					indicatedPaymentResponsePayeeLengthBytes[i] = bytes[index++];
				}
				
				int paymentResponsePayeeLength = (Utils.getBytesAsShort(indicatedPaymentResponsePayeeLengthBytes) & 0xFF);
				byte[] paymentResponsePayeeBytes = new byte[paymentResponsePayeeLength];
				for (int i=0; i<paymentResponsePayeeLength; i++) {
					paymentResponsePayeeBytes[i] = bytes[index++];
				}
				
				PaymentResponse paymentResponsePayee = DecoderFactory.decode(PaymentResponse.class, paymentResponsePayeeBytes);
				return new ServerPaymentResponse(version, paymentResponsePayer, paymentResponsePayee);
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
		if (!(o instanceof ServerPaymentResponse))
			return false;
		
		ServerPaymentResponse spr = (ServerPaymentResponse) o;
		if (getVersion() != spr.getVersion())
			return false;
		if (this.nofPaymentResponses != spr.nofPaymentResponses)
			return false;
		if (!getPaymentResponsePayer().equals(spr.getPaymentResponsePayer()))
			return false;
		if (nofPaymentResponses == 2) {
			if (!getPaymentResponsePayer().equals(spr.getPaymentResponsePayer()))
				return false;
		}
		
		return true;
	}

}
