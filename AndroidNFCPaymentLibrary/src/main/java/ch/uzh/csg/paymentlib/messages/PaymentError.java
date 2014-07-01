package ch.uzh.csg.paymentlib.messages;

import java.util.HashMap;
import java.util.Map;

import ch.uzh.csg.paymentlib.exceptions.UnknownPaymentErrorException;

/**
 * This class contains all errors which might occur during a payment. If a
 * {@link PaymentMessage} contains the ERROR flag in the header, then the
 * payload is set to one of the codes below to provide more information why a
 * payment was not successful.
 * 
 * @author Jeton Memeti
 * 
 */
public enum PaymentError {
	PAYER_REFUSED((byte) 0x01),
	SERVER_REFUSED((byte) 0x02),
	REQUESTS_NOT_IDENTIC((byte) 0x03),
	DUPLICATE_REQUEST((byte) 0x04),
	NO_SERVER_RESPONSE((byte) 0x05), //when no server response received (neither ok nor nok) --> show on gui
	UNEXPECTED_ERROR((byte) 0x06);
	
	private byte code;
	
	private PaymentError(byte code) {
		this.code = code;
	}
	
	/**
	 * Returns the code of the given error.
	 */
	public byte getCode() {
		return code;
	}
	
	private static Map<Byte, PaymentError> codeErrorMap = null;
	
	/**
	 * Returns the PaymentError from the code.
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
