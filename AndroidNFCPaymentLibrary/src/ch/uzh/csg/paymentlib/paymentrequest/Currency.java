package ch.uzh.csg.paymentlib.paymentrequest;

import java.util.HashMap;
import java.util.Map;

/**
 * This class contains the supported currencies.
 * 
 * @author Jeton Memeti
 * 
 */
public enum Currency {
	BTC((byte) 0x01);
	
	private byte code;
	
	private Currency(byte code) {
		this.code = code;
	}

	/**
	 * Returns the code of this Currency.
	 */
	public byte getCode() {
		return code;
	}
	
	private static Map<Byte, Currency> codeCurrencyMap = null;
	
	/**
	 * Returns the Currency based on the code.
	 * 
	 * @param b
	 *            the code
	 */
	public static Currency getCurrency(byte b) {
		if (codeCurrencyMap == null)
			initMap();
		
		return codeCurrencyMap.get(b);
	}

	private static void initMap() {
		codeCurrencyMap = new HashMap<Byte, Currency>();
		for (Currency c : values()) {
			codeCurrencyMap.put(c.getCode(), c);
		}
	}
	
}
