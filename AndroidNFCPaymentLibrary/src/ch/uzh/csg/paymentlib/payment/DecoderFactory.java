package ch.uzh.csg.paymentlib.payment;

import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;
import ch.uzh.csg.paymentlib.exceptions.NotSignedException;
import ch.uzh.csg.paymentlib.exceptions.UnknownCurrencyException;
import ch.uzh.csg.paymentlib.exceptions.UnknownSignatureAlgorithmException;

//TODO: javadoc
public class DecoderFactory {
	
	@SuppressWarnings("unchecked")
	public static <T extends SerializableObject> T decode(Class<? extends SerializableObject> clazz, byte[] bytes) throws IllegalAccessException, IllegalArgumentException, NotSignedException, UnknownSignatureAlgorithmException, UnknownCurrencyException {
		try {
			T t = (T) clazz.newInstance();
			t.decode(bytes);
			return t;
		} catch (InstantiationException e) {
			return null;
		}
	}
	
}
