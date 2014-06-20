package ch.uzh.csg.paymentlib.messages;

//TODO: javadoc
public class PaymentMessage {

	public static final int HEADER_LENGTH = 1;
	
	public static final byte DEFAULT = 0x00;
	public static final byte ERROR = 0x01; // if not set, then PROCEED
	public static final byte PAYER = 0x02; // if not set, then PAYEE

	// data
	private byte[] payload = new byte[0];
	private int header = DEFAULT;
	
	public PaymentMessage error() {
		header = header | ERROR;
		return this;
	}
	
	public boolean isError() {
		return (header & ERROR) == ERROR;
	}
	
	public PaymentMessage payer() {
		header = header | PAYER;
		return this;
	}
	
	public boolean isPayer() {
		return (header & PAYER) == PAYER;
	}
	
	public PaymentMessage payee() {
		header = header & ~PAYER;
		return this;
	}
	
	public boolean isPayee() {
		return (header & PAYER) != PAYER;
	}

	public PaymentMessage payload(byte[] payload) {
		if (payload == null || payload.length == 0)
			throw new IllegalArgumentException("payload cannot be null or empty");
		
		this.payload = payload;
		return this;
	}

	public byte[] payload() {
		return payload;
	}
	
	public byte header() {
		return (byte) header;
	}
	
	// serialization
	public byte[] bytes() {
		final int len = payload.length;
		byte[] output = new byte[HEADER_LENGTH + len];
		output[0] = (byte) header;
		System.arraycopy(payload, 0, output, HEADER_LENGTH, len);
		return output;
	}

	public PaymentMessage bytes(byte[] input) {
		final int len = input.length;
		if (!isEmpty())
			throw new IllegalArgumentException("This message is not empty. You cannot overwrite the content. Instantiate a new object.");
		
		if (input == null || len < HEADER_LENGTH)
			throw new IllegalArgumentException("The input is null or does not contain enough data.");

		header = input[0];
		if (len > HEADER_LENGTH) {
			payload = new byte[len - HEADER_LENGTH];
			System.arraycopy(input, HEADER_LENGTH, payload, 0, len - HEADER_LENGTH);
		}
		return this;
	}
	
	private boolean isEmpty() {
		return header == 0 && payload.length == 0;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("PaymentMsg: ");
		sb.append("head: ").append(Integer.toHexString(header));
		sb.append(", len:").append(payload.length);
		return sb.toString();
	}
	
}
