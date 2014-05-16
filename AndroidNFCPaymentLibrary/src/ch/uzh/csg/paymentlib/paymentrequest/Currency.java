package ch.uzh.csg.paymentlib.paymentrequest;

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
	
}
