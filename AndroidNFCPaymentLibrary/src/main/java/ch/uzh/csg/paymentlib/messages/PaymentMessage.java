package ch.uzh.csg.paymentlib.messages;

import ch.uzh.csg.nfclib.messages.ProtocolMessage;

//TODO: javadoc
public class PaymentMessage extends ProtocolMessage {

	public static final byte DEFAULT = 0x01;
	public static final byte ERROR = 0x02; // if not set, then PROCEED
	public static final byte RESUME = 0x04; // if not set, then START //TODO: needed??
	public static final byte BUYER = 0x08; // if not set, then SELLER

	public static final int HEADER_LENGTH = 1;
	
	public PaymentMessage(byte[] data) {
		setHeaderLength(HEADER_LENGTH);
		setData(data);
	}
	
	/**
	 * Creates a new PaymentMessage
	 * 
	 * @param status
	 *            the status to e contained in the header
	 * @param payload
	 *            the payload of this message
	 */
	public PaymentMessage(byte status, byte[] payload) {
		byte[] data;
		if (payload != null && payload.length > 0) {
			data = new byte[payload.length+HEADER_LENGTH];
			System.arraycopy(payload, 0, data, HEADER_LENGTH, payload.length);
		} else {
			data = new byte[HEADER_LENGTH];
		}
		
		data[0] = status;
		
		setHeaderLength(HEADER_LENGTH);
		setData(data);
	}

	/**
	 * Returns true if an error has been encountered and the protocol should be
	 * aborted.
	 */
	public boolean isError() {
		return (getStatus() & ERROR) == ERROR;
	}

	/**
	 * Returns true if this PaymentMessage belongs to an ongoing communication.
	 * It returns false if a new communication is started.
	 */
	public boolean isResume() {
		return (getStatus() & RESUME) == RESUME;
	}
	
	/**
	 * Returns true if the buyer initiated the protocol.
	 */
	public boolean isBuyer() {
		return (getStatus() & BUYER) == BUYER;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("PaymentMessage: ");
		if (getData() == null || getData().length < 1) {
			sb.append("corrupt message!");
			return sb.toString();
		} else {
			sb.append("head: ");
			sb.append(", status: ").append(Integer.toHexString(getData()[0] & 0xFF));
			sb.append("/ payload length:").append(getData().length-HEADER_LENGTH);
			return sb.toString();
		}
	}
	
}
