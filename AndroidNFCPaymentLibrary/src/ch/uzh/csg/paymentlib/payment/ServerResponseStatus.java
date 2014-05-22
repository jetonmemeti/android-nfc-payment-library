package ch.uzh.csg.paymentlib.payment;

import java.util.HashMap;
import java.util.Map;

/**
 * This class contains the possible server response status.
 * 
 * @author Jeton Memeti
 * 
 */
public enum ServerResponseStatus {
	SUCCESS((byte) 0x01),
	FAILURE((byte) 0x02);

	private byte code;
	
	private ServerResponseStatus(byte code) {
		this.code = code;
	}
	
	/**
	 * Returns the code of this ServerResponseStatus
	 */
	public byte getCode() {
		return code;
	}
	
	private static Map<Byte, ServerResponseStatus> codeStatusMap = null;
	
	/**
	 * Returns the ServerResponseStatus based on the code.
	 * 
	 * @param b
	 *            the code
	 */
	public static ServerResponseStatus getStatus(byte code) {
		if (codeStatusMap == null)
			initMap();
		
		return codeStatusMap.get(code);
	}
	
	private static void initMap() {
		codeStatusMap = new HashMap<Byte, ServerResponseStatus>();
		for (ServerResponseStatus s : values()) {
			codeStatusMap.put(s.getCode(), s);
		}
	}
	
		
}
