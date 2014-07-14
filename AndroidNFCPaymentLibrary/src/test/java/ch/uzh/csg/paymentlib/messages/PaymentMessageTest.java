package ch.uzh.csg.paymentlib.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ch.uzh.csg.paymentlib.messages.PaymentMessage;

public class PaymentMessageTest {

	@Test
	public void testHeader() {
		PaymentMessage m = new PaymentMessage();
		assertEquals(0, m.version());
		
		byte header = (byte) 0xC3; // 11000011
		PaymentMessage m2 = new PaymentMessage().bytes(new byte[] { header });
		assertTrue(m2.isPayer()); // bit 7
		assertTrue(m2.isError()); // bit 8
		assertEquals(3, m2.version()); // bits 1+2
	}
	
}
