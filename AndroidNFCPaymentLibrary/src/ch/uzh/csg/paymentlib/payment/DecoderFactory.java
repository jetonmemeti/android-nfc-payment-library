package ch.uzh.csg.paymentlib.payment;

import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;
import ch.uzh.csg.paymentlib.exceptions.NotSignedException;
import ch.uzh.csg.paymentlib.exceptions.UnknownCurrencyException;
import ch.uzh.csg.paymentlib.exceptions.UnknownSignatureAlgorithmException;

//TODO: javadoc
public class DecoderFactory {
	
	@SuppressWarnings("unchecked")
	public static <T extends SerializableObject> T decode(Class<? extends SerializableObject> clazz, byte[] bytes) throws IllegalArgumentException, NotSignedException, UnknownSignatureAlgorithmException, UnknownCurrencyException {
		try {
			T t = (T) clazz.newInstance();
			return (T) t.decode(bytes);
		} catch (InstantiationException e) {
			return null;
		} catch (IllegalAccessException e) {
			return null;
		}
	}
	
}
