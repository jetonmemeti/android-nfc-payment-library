package ch.uzh.csg.paymentlib.messages;

import java.util.HashMap;
import java.util.Map;

import ch.uzh.csg.paymentlib.exceptions.UnknownPaymentErrorException;

//TODO: javadoc
public enum PaymentError {
	PAYER_REFUSED((byte) 0x01),
	SERVER_REFUSED((byte) 0x02),
	REQUESTS_NOT_IDENTIC((byte) 0x03),
	UNEXPECTED_ERROR((byte) 0x04);
	//TODO: add duplicate request!
	
	private byte code;
	
	private PaymentError(byte code) {
		this.code = code;
	}
	
	public byte getCode() {
		return code;
	}
	
	private static Map<Byte, PaymentError> codeErrorMap = null;
	
	/**
	 * Returns the PaymentError on the code.
	 * 
	 * @param b
	 *            the code
	 * @throws UnknownPaymentErrorException
	 *             if the given code is not known
	 */
	public static PaymentError getPaymentError(byte b) throws UnknownPaymentErrorException {
		if (codeErrorMap == null)
			initMap();
		
		PaymentError err = codeErrorMap.get(b);
		if (err == null)
			throw new UnknownPaymentErrorException();
		else
			return err;
	}

	private static void initMap() {
		codeErrorMap = new HashMap<Byte, PaymentError>();
		for (PaymentError err : values()) {
			codeErrorMap.put(err.getCode(), err);
		}
	}

}
