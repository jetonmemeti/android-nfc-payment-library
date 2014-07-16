package ch.uzh.csg.paymentlib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import android.app.Activity;
import android.util.Log;
import ch.uzh.csg.mbps.customserialization.Currency;
import ch.uzh.csg.mbps.customserialization.DecoderFactory;
import ch.uzh.csg.mbps.customserialization.InitMessagePayee;
import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.customserialization.PaymentRequest;
import ch.uzh.csg.mbps.customserialization.PaymentResponse;
import ch.uzh.csg.mbps.customserialization.ServerPaymentRequest;
import ch.uzh.csg.mbps.customserialization.ServerPaymentResponse;
import ch.uzh.csg.mbps.customserialization.ServerResponseStatus;
import ch.uzh.csg.nfclib.ISendLater;
import ch.uzh.csg.nfclib.events.NfcEvent;
import ch.uzh.csg.paymentlib.PaymentRequestHandler.MessageHandler;
import ch.uzh.csg.paymentlib.container.PaymentInfos;
import ch.uzh.csg.paymentlib.container.ServerInfos;
import ch.uzh.csg.paymentlib.container.UserInfos;
import ch.uzh.csg.paymentlib.messages.PaymentError;
import ch.uzh.csg.paymentlib.messages.PaymentMessage;
import ch.uzh.csg.paymentlib.testutils.PersistencyHandler;
import ch.uzh.csg.paymentlib.testutils.TestUtils;
import ch.uzh.csg.paymentlib.util.Config;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Log.class)
public class PaymentRequestHandlerTest {
	
	private Activity hostActivity = Mockito.mock(Activity.class);
	
	private class State {
		private PaymentEvent event;
		private Object object;
		@SuppressWarnings("unused")
		private IServerResponseListener caller;
		
		private State(PaymentEvent event, Object object, IServerResponseListener caller) {
			this.event = event;
			this.object = object;
			this.caller = caller;
		}
	}
	
	private List<State> states = new ArrayList<State>();
	private PersistencyHandler persistencyHandler = null;
	private byte[] sendLaterBytes = null;

	private void reset() {
		states.clear();
		persistencyHandler = new PersistencyHandler();
		sendLaterBytes = null;
	}
	
	@Before
	public void before() {
		PowerMockito.mockStatic(Log.class);
		Answer<Integer> answer = new Answer<Integer>() {
			@Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
	            System.err.println(Arrays.toString(invocation.getArguments()));
	            return 0;
            }
		};
		PowerMockito.when(Log.i(Mockito.anyString(), Mockito.anyString())).then(answer);
		PowerMockito.when(Log.d(Mockito.anyString(), Mockito.anyString())).then(answer);
		PowerMockito.when(Log.e(Mockito.anyString(), Mockito.anyString())).then(answer);
	}
	
	private IPaymentEventHandler paymentEventHandler = new IPaymentEventHandler() {
		@Override
		public void handleMessage(PaymentEvent event, Object object, IServerResponseListener caller) {
			states.add(new State(event, object, caller));
		}
	};
	
	private IUserPromptPaymentRequest defaultUserPrompt = new IUserPromptPaymentRequest() {

		@Override
		public boolean isPaymentAccepted() {
			// accept the payment
			return true;
		}

		@Override
		public void promptUserPaymentRequest(String username, Currency currency, long amount, IUserPromptAnswer answer) {
			// accept the payment
			answer.acceptPayment();
		}
	};
	
	private ISendLater sendLater = new ISendLater() {
		@Override
		public void sendLater(byte[] arg0) {
			sendLaterBytes = arg0;
		}
	};
	
	@Test
	public void testPaymentRequestHandler_Payee_ServerRefuses() throws Exception {
		/*
		 * Simulates server refuses the payment for any reason
		 */
		reset();
		
		KeyPair keyPairPayee = TestUtils.generateKeyPair();
		UserInfos userInfosPayee = new UserInfos("seller", keyPairPayee.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		PaymentInfos paymentInfos = new PaymentInfos(Currency.BTC, 1);
		
		KeyPair keyPairPayer = TestUtils.generateKeyPair();
		KeyPair keyPairServer = TestUtils.generateKeyPair();
		UserInfos userInfosPayer = new UserInfos("buyer", keyPairPayer.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		ServerInfos serverInfos = new ServerInfos(keyPairServer.getPublic());
		
		PaymentRequestHandler prh = new PaymentRequestHandler(hostActivity, paymentEventHandler, userInfosPayer, serverInfos, defaultUserPrompt, persistencyHandler);
		MessageHandler messageHandler = prh.getMessageHandler();
		prh.getNfcEventHandler().handleMessage(NfcEvent.INITIALIZED, null);
		
		// receive payment request
		InitMessagePayee initMessage = new InitMessagePayee(userInfosPayee.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount());
		PaymentMessage pm = new PaymentMessage().payee().payload(initMessage.encode());
		assertTrue(pm.isPayee());
		
		byte[] handleMessage = messageHandler.handleMessage(pm.bytes(), sendLater);
		assertNull(handleMessage);
		
		assertNotNull(sendLaterBytes);
		pm = new PaymentMessage().bytes(sendLaterBytes);
		assertFalse(pm.isError());
		sendLaterBytes = null;
		
		PaymentRequest paymentRequestPayer = DecoderFactory.decode(PaymentRequest.class, pm.payload());
		assertEquals(userInfosPayer.getUsername(), paymentRequestPayer.getUsernamePayer());
		assertEquals(userInfosPayee.getUsername(), paymentRequestPayer.getUsernamePayee());
		assertEquals(paymentInfos.getCurrency().getCode(), paymentRequestPayer.getCurrency().getCode());
		assertEquals(paymentInfos.getAmount(), paymentRequestPayer.getAmount());
		
		// receive payment response from server
		PaymentRequest paymentRequestPayee = new PaymentRequest(userInfosPayee.getPKIAlgorithm(), userInfosPayee.getKeyNumber(), paymentRequestPayer.getUsernamePayer(), userInfosPayee.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount(), paymentRequestPayer.getTimestamp());
		assertTrue(paymentRequestPayer.requestsIdentic(paymentRequestPayee));
		paymentRequestPayee.sign(userInfosPayer.getPrivateKey());
		ServerPaymentRequest spr = new ServerPaymentRequest(paymentRequestPayer, paymentRequestPayee);
		
		ServerPaymentRequest decode = DecoderFactory.decode(ServerPaymentRequest.class, spr.encode());
		PaymentRequest paymentRequestPayer2 = decode.getPaymentRequestPayer();
		
		String reason = "any reason";
		PaymentResponse pr = new PaymentResponse(PKIAlgorithm.DEFAULT, 1, ServerResponseStatus.FAILURE, reason, paymentRequestPayer2.getUsernamePayer(), paymentRequestPayer2.getUsernamePayee(), paymentRequestPayer2.getCurrency(), paymentRequestPayer2.getAmount(), paymentRequestPayer2.getTimestamp());
		pr.sign(keyPairServer.getPrivate());
		ServerPaymentResponse spr2 = new ServerPaymentResponse(pr);
		byte[] encode = spr2.encode();
		
		ServerPaymentResponse serverPaymentResponse = DecoderFactory.decode(ServerPaymentResponse.class, encode);
		byte[] encode2 = serverPaymentResponse.getPaymentResponsePayer().encode();
		
		byte[] data = new PaymentMessage().payee().payload(encode2).bytes();
		
		byte[] handleMessage2 = messageHandler.handleMessage(data, sendLater);
		assertNotNull(handleMessage2);
		assertNull(sendLaterBytes);
		
		PaymentMessage pm2 = new PaymentMessage().bytes(handleMessage2);
		assertFalse(pm2.isError());
		assertEquals(1, pm2.payload().length);
		assertEquals(PaymentRequestHandler.ACK[0], pm2.payload()[0]);
		
		assertEquals(2, states.size());
		State state = states.get(0);
		assertEquals(PaymentEvent.INITIALIZED, state.event);
		state = states.get(1);
		assertEquals(PaymentEvent.ERROR, state.event);
		assertNotNull(state.object);
		assertTrue(state.object instanceof PaymentError);
		PaymentError err = (PaymentError) state.object;
		assertEquals(PaymentError.SERVER_REFUSED, err);
	}
	
	@Test
	public void testPaymentRequestHandler_Payee_Success() throws Exception {
		/*
		 * Simulates a successful payment
		 */
		reset();

		KeyPair keyPairPayee = TestUtils.generateKeyPair();
		UserInfos userInfosPayee = new UserInfos("seller", keyPairPayee.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		PaymentInfos paymentInfos = new PaymentInfos(Currency.BTC, 1);
		
		KeyPair keyPairPayer = TestUtils.generateKeyPair();
		KeyPair keyPairServer = TestUtils.generateKeyPair();
		UserInfos userInfosPayer = new UserInfos("buyer", keyPairPayer.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		ServerInfos serverInfos = new ServerInfos(keyPairServer.getPublic());
		
		PaymentRequestHandler prh = new PaymentRequestHandler(hostActivity, paymentEventHandler, userInfosPayer, serverInfos, defaultUserPrompt, persistencyHandler);
		MessageHandler messageHandler = prh.getMessageHandler();
		prh.getNfcEventHandler().handleMessage(NfcEvent.INITIALIZED, null);
		
		// receive payment request
		InitMessagePayee initMessage = new InitMessagePayee(userInfosPayee.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount());
		byte[] data = new PaymentMessage().payee().payload(initMessage.encode()).bytes();
		
		byte[] handleMessage = messageHandler.handleMessage(data, sendLater);
		assertNull(handleMessage);
		
		assertNotNull(sendLaterBytes);
		PaymentMessage pm = new PaymentMessage().bytes(sendLaterBytes);
		assertFalse(pm.isError());
		sendLaterBytes = null;

		PaymentRequest paymentRequestPayer = DecoderFactory.decode(PaymentRequest.class, pm.payload());
		assertEquals(userInfosPayer.getUsername(), paymentRequestPayer.getUsernamePayer());
		assertEquals(userInfosPayee.getUsername(), paymentRequestPayer.getUsernamePayee());
		assertEquals(paymentInfos.getCurrency().getCode(), paymentRequestPayer.getCurrency().getCode());
		assertEquals(paymentInfos.getAmount(), paymentRequestPayer.getAmount());
		
		// receive payment response
		PaymentRequest paymentRequestPayee = new PaymentRequest(userInfosPayee.getPKIAlgorithm(), userInfosPayee.getKeyNumber(), paymentRequestPayer.getUsernamePayer(), userInfosPayee.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount(), paymentRequestPayer.getTimestamp());
		assertTrue(paymentRequestPayer.requestsIdentic(paymentRequestPayee));
		paymentRequestPayee.sign(userInfosPayer.getPrivateKey());
		ServerPaymentRequest spr = new ServerPaymentRequest(paymentRequestPayer, paymentRequestPayee);
		
		ServerPaymentRequest decode = DecoderFactory.decode(ServerPaymentRequest.class, spr.encode());
		PaymentRequest paymentRequestPayer2 = decode.getPaymentRequestPayer();
		
		PaymentResponse pr = new PaymentResponse(PKIAlgorithm.DEFAULT, 1, ServerResponseStatus.SUCCESS, null, paymentRequestPayer2.getUsernamePayer(), paymentRequestPayer2.getUsernamePayee(), paymentRequestPayer2.getCurrency(), paymentRequestPayer2.getAmount(), paymentRequestPayer2.getTimestamp());
		pr.sign(keyPairServer.getPrivate());
		ServerPaymentResponse spr2 = new ServerPaymentResponse(pr);
		byte[] encode = spr2.encode();
		
		ServerPaymentResponse serverPaymentResponse = DecoderFactory.decode(ServerPaymentResponse.class, encode);
		byte[] encode2 = serverPaymentResponse.getPaymentResponsePayer().encode();
		
		data = new PaymentMessage().payee().payload(encode2).bytes();
		
		byte[] handleMessage2 = messageHandler.handleMessage(data, sendLater);
		assertNotNull(handleMessage2);
		assertNull(sendLaterBytes);
		
		PaymentMessage pm2 = new PaymentMessage().bytes(handleMessage2);
		assertFalse(pm2.isError());
		assertEquals(1, pm2.payload().length);
		assertEquals(PaymentRequestHandler.ACK[0], pm2.payload()[0]);
		
		assertEquals(2, states.size());
		State state = states.get(0);
		assertEquals(PaymentEvent.INITIALIZED, state.event);
		state = states.get(1);
		assertEquals(PaymentEvent.SUCCESS, state.event);
		assertNotNull(state.object);
		assertTrue(state.object instanceof PaymentResponse);
		PaymentResponse pr1 = (PaymentResponse) state.object;
		assertEquals(userInfosPayer.getUsername(), pr1.getUsernamePayer());
		assertEquals(userInfosPayee.getUsername(), pr1.getUsernamePayee());
	}
	
	@Test
	public void testPaymentRequestHandler_Payee_ServerResponseTimeout() throws Exception {
		/*
		 * Simulates a server response timeout for the case where the payee
		 * accepts or rejects the payment request while the devices are always
		 * connected
		 */
		reset();

		KeyPair keyPairPayee = TestUtils.generateKeyPair();
		UserInfos userInfosPayee = new UserInfos("seller", keyPairPayee.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		PaymentInfos paymentInfos = new PaymentInfos(Currency.BTC, 1);
		
		KeyPair keyPairPayer = TestUtils.generateKeyPair();
		KeyPair keyPairServer = TestUtils.generateKeyPair();
		UserInfos userInfosPayer = new UserInfos("buyer", keyPairPayer.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		ServerInfos serverInfos = new ServerInfos(keyPairServer.getPublic());
		
		PaymentRequestHandler prh = new PaymentRequestHandler(hostActivity, paymentEventHandler, userInfosPayer, serverInfos, defaultUserPrompt, persistencyHandler);
		MessageHandler messageHandler = prh.getMessageHandler();
		prh.getNfcEventHandler().handleMessage(NfcEvent.INITIALIZED, null);
		
		// receive payment request
		InitMessagePayee initMessage = new InitMessagePayee(userInfosPayee.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount());
		byte[] data = new PaymentMessage().payee().payload(initMessage.encode()).bytes();
		
		byte[] handleMessage = messageHandler.handleMessage(data, sendLater);
		assertNull(handleMessage);
		
		assertNotNull(sendLaterBytes);
		PaymentMessage pm = new PaymentMessage().bytes(sendLaterBytes);
		assertFalse(pm.isError());
		sendLaterBytes = null;
		
		PaymentRequest paymentRequestPayer = DecoderFactory.decode(PaymentRequest.class, pm.payload());
		assertEquals(userInfosPayer.getUsername(), paymentRequestPayer.getUsernamePayer());
		assertEquals(userInfosPayee.getUsername(), paymentRequestPayer.getUsernamePayee());
		assertEquals(paymentInfos.getCurrency().getCode(), paymentRequestPayer.getCurrency().getCode());
		assertEquals(paymentInfos.getAmount(), paymentRequestPayer.getAmount());
		
		Thread.sleep(Config.SERVER_RESPONSE_TIMEOUT+500);
		
		assertEquals(2, states.size());
		State state = states.get(0);
		assertEquals(PaymentEvent.INITIALIZED, state.event);
		state = states.get(1);
		assertEquals(PaymentEvent.ERROR, state.event);
		assertNotNull(state.object);
		assertTrue(state.object instanceof PaymentError);
		PaymentError err = (PaymentError) state.object;
		assertEquals(PaymentError.NO_SERVER_RESPONSE, err);
	}
	
	@Test
	public void testPaymentRequestHandler_Payee_ServerResponseTimeout_interruptConnection() throws Exception {
		/*
		 * Simulates a server response timeout for the case where the payee
		 * accepts or rejects the payment request after the NFC connection has
		 * been aborted and the devices are re-connected after the payee took
		 * his decision.
		 */
		reset();

		KeyPair keyPairPayee = TestUtils.generateKeyPair();
		UserInfos userInfosPayee = new UserInfos("seller", keyPairPayee.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		PaymentInfos paymentInfos = new PaymentInfos(Currency.BTC, 1);
		
		KeyPair keyPairPayer = TestUtils.generateKeyPair();
		KeyPair keyPairServer = TestUtils.generateKeyPair();
		UserInfos userInfosPayer = new UserInfos("buyer", keyPairPayer.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		ServerInfos serverInfos = new ServerInfos(keyPairServer.getPublic());
		
		PaymentRequestHandler prh = new PaymentRequestHandler(hostActivity, paymentEventHandler, userInfosPayer, serverInfos, defaultUserPrompt, persistencyHandler);
		MessageHandler messageHandler = prh.getMessageHandler();
		prh.getNfcEventHandler().handleMessage(NfcEvent.INITIALIZED, null);
		
		/*
		 * In real life, the connection would be aborted once the user sees the
		 * prompt and removes the device to click on 'accept' or 'reject'.
		 * Instead of refactoring the IUserPromptPaymentRequest, we can fire the
		 * connection lost event here which results in the same behavior.
		 */
		prh.getNfcEventHandler().handleMessage(NfcEvent.CONNECTION_LOST, null);
		
		// receive payment request
		InitMessagePayee initMessage = new InitMessagePayee(userInfosPayee.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount());
		byte[] data = new PaymentMessage().payee().payload(initMessage.encode()).bytes();
		
		byte[] handleMessage = messageHandler.handleMessage(data, sendLater);
		assertNull(handleMessage);
		
		assertNotNull(sendLaterBytes);
		PaymentMessage pm = new PaymentMessage().bytes(sendLaterBytes);
		assertFalse(pm.isError());
		sendLaterBytes = null;
		
		PaymentRequest paymentRequestPayer = DecoderFactory.decode(PaymentRequest.class, pm.payload());
		assertEquals(userInfosPayer.getUsername(), paymentRequestPayer.getUsernamePayer());
		assertEquals(userInfosPayee.getUsername(), paymentRequestPayer.getUsernamePayee());
		assertEquals(paymentInfos.getCurrency().getCode(), paymentRequestPayer.getCurrency().getCode());
		assertEquals(paymentInfos.getAmount(), paymentRequestPayer.getAmount());
		
		/*
		 * Once the NFC connection is re-established, the timeout task starts,
		 * because this is where the signed payment request is sent to the
		 * counterpart. If no response arrives in the given time, the no server
		 * response event is fired.
		 */
		prh.getNfcEventHandler().handleMessage(NfcEvent.INITIALIZED, null);
		
		Thread.sleep(Config.SERVER_RESPONSE_TIMEOUT+500);
		
		/*
		 * This simulates that the counterpart sends the server response after
		 * the payee has run into the timeout. The payee should not process that
		 * data and return an unexpected error.
		 */
		data = new PaymentMessage().payee().payload(new byte[] { 0x00, 0x01, 0x02 }).bytes();
		handleMessage = messageHandler.handleMessage(data, sendLater);
		pm = new PaymentMessage().bytes(handleMessage);
		assertNull(sendLaterBytes);
		assertTrue(pm.isError());
		assertEquals(PaymentError.UNEXPECTED_ERROR.getCode(), pm.payload()[0]);
		
		assertEquals(3, states.size());
		State state = states.get(0);
		assertEquals(PaymentEvent.INITIALIZED, state.event);
		state = states.get(1);
		assertEquals(PaymentEvent.INITIALIZED, state.event);
		state = states.get(2);
		assertEquals(PaymentEvent.ERROR, state.event);
		assertNotNull(state.object);
		assertTrue(state.object instanceof PaymentError);
		PaymentError err = (PaymentError) state.object;
		assertEquals(PaymentError.NO_SERVER_RESPONSE, err);
	}
	
	@Test
	public void testPaymentRequestHandler_Payee_PersistencyHandler() throws Exception {
		/*
		 * Simulates a successful payment
		 */
		reset();

		KeyPair keyPairPayee = TestUtils.generateKeyPair();
		UserInfos userInfosPayee = new UserInfos("seller", keyPairPayee.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		PaymentInfos paymentInfos = new PaymentInfos(Currency.BTC, 1);
		
		KeyPair keyPairPayer = TestUtils.generateKeyPair();
		KeyPair keyPairServer = TestUtils.generateKeyPair();
		UserInfos userInfosPayer = new UserInfos("buyer", keyPairPayer.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		ServerInfos serverInfos = new ServerInfos(keyPairServer.getPublic());
		
		PaymentRequestHandler prh = new PaymentRequestHandler(hostActivity, paymentEventHandler, userInfosPayer, serverInfos, defaultUserPrompt, persistencyHandler);
		MessageHandler messageHandler = prh.getMessageHandler();
		prh.getNfcEventHandler().handleMessage(NfcEvent.INITIALIZED, null);
		
		// receive payment request
		InitMessagePayee initMessage = new InitMessagePayee(userInfosPayee.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount());
		byte[] data = new PaymentMessage().payee().payload(initMessage.encode()).bytes();
		
		byte[] handleMessage = messageHandler.handleMessage(data, sendLater);
		assertNull(handleMessage);
		
		assertNotNull(sendLaterBytes);
		PaymentMessage pm = new PaymentMessage().bytes(sendLaterBytes);
		assertFalse(pm.isError());
		sendLaterBytes = null;
		
		PaymentRequest paymentRequestPayer = DecoderFactory.decode(PaymentRequest.class, pm.payload());
		assertEquals(userInfosPayer.getUsername(), paymentRequestPayer.getUsernamePayer());
		assertEquals(userInfosPayee.getUsername(), paymentRequestPayer.getUsernamePayee());
		assertEquals(paymentInfos.getCurrency().getCode(), paymentRequestPayer.getCurrency().getCode());
		assertEquals(paymentInfos.getAmount(), paymentRequestPayer.getAmount());
		
		assertEquals(1, persistencyHandler.getList().size());
		
		// receive payment response
		PaymentRequest paymentRequestPayee = new PaymentRequest(userInfosPayee.getPKIAlgorithm(), userInfosPayee.getKeyNumber(), paymentRequestPayer.getUsernamePayer(), userInfosPayee.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount(), paymentRequestPayer.getTimestamp());
		assertTrue(paymentRequestPayer.requestsIdentic(paymentRequestPayee));
		paymentRequestPayee.sign(userInfosPayer.getPrivateKey());
		ServerPaymentRequest spr = new ServerPaymentRequest(paymentRequestPayer, paymentRequestPayee);
		
		ServerPaymentRequest decode = DecoderFactory.decode(ServerPaymentRequest.class, spr.encode());
		PaymentRequest paymentRequestPayer2 = decode.getPaymentRequestPayer();
		
		PaymentResponse pr = new PaymentResponse(PKIAlgorithm.DEFAULT, 1, ServerResponseStatus.SUCCESS, null, paymentRequestPayer2.getUsernamePayer(), paymentRequestPayer2.getUsernamePayee(), paymentRequestPayer2.getCurrency(), paymentRequestPayer2.getAmount(), paymentRequestPayer2.getTimestamp());
		pr.sign(keyPairServer.getPrivate());
		ServerPaymentResponse spr2 = new ServerPaymentResponse(pr);
		byte[] encode = spr2.encode();
		
		ServerPaymentResponse serverPaymentResponse = DecoderFactory.decode(ServerPaymentResponse.class, encode);
		byte[] encode2 = serverPaymentResponse.getPaymentResponsePayer().encode();
		
		pm = new PaymentMessage().payee().payload(encode2);
		
		byte[] handleMessage2 = messageHandler.handleMessage(pm.bytes(), sendLater);
		assertNotNull(handleMessage2);
		assertNull(sendLaterBytes);
		
		PaymentMessage pm2 = new PaymentMessage().bytes(handleMessage2);
		assertFalse(pm2.isError());
		assertEquals(1, pm2.payload().length);
		assertEquals(PaymentRequestHandler.ACK[0], pm2.payload()[0]);
		
		assertEquals(0, persistencyHandler.getList().size());
		
		assertEquals(2, states.size());
		State state = states.get(0);
		assertEquals(PaymentEvent.INITIALIZED, state.event);
		state = states.get(1);
		assertEquals(PaymentEvent.SUCCESS, state.event);
		assertNotNull(state.object);
		assertTrue(state.object instanceof PaymentResponse);
		PaymentResponse pr1 = (PaymentResponse) state.object;
		assertEquals(userInfosPayer.getUsername(), pr1.getUsernamePayer());
		assertEquals(userInfosPayee.getUsername(), pr1.getUsernamePayee());
	}
	
	@Test
	public void testPaymentRequestHandler_Payee_PayeeRemovesDevice() throws Exception {
		/*
		 * Simulates a payment where the payer removes the device to click on
		 * the accept button and re-establishes a nfc contact
		 */
		reset();

		KeyPair keyPairPayee = TestUtils.generateKeyPair();
		UserInfos userInfosPayee = new UserInfos("seller", keyPairPayee.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		PaymentInfos paymentInfos = new PaymentInfos(Currency.BTC, 1);

		KeyPair keyPairPayer = TestUtils.generateKeyPair();
		KeyPair keyPairServer = TestUtils.generateKeyPair();
		UserInfos userInfosPayer = new UserInfos("buyer", keyPairPayer.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		ServerInfos serverInfos = new ServerInfos(keyPairServer.getPublic());

		PaymentRequestHandler prh = new PaymentRequestHandler(hostActivity, paymentEventHandler, userInfosPayer, serverInfos, defaultUserPrompt, persistencyHandler);
		MessageHandler messageHandler = prh.getMessageHandler();
		prh.getNfcEventHandler().handleMessage(NfcEvent.INITIALIZED, null);

		// receive payment request
		InitMessagePayee initMessage = new InitMessagePayee(userInfosPayee.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount());
		byte[] data = new PaymentMessage().payee().payload(initMessage.encode()).bytes();

		byte[] handleMessage = messageHandler.handleMessage(data, sendLater);
		assertNull(handleMessage);
		assertNotNull(sendLaterBytes);
		PaymentMessage pm = new PaymentMessage().bytes(sendLaterBytes);
		assertFalse(pm.isError());
		sendLaterBytes = null;
		
		PaymentRequest paymentRequestPayer = DecoderFactory.decode(PaymentRequest.class, pm.payload());
		assertEquals(userInfosPayer.getUsername(), paymentRequestPayer.getUsernamePayer());
		assertEquals(userInfosPayee.getUsername(), paymentRequestPayer.getUsernamePayee());
		assertEquals(paymentInfos.getCurrency().getCode(), paymentRequestPayer.getCurrency().getCode());
		assertEquals(paymentInfos.getAmount(), paymentRequestPayer.getAmount());

		assertEquals(1, persistencyHandler.getList().size());

		// assume that the payer removes his device - on re-connect receive the same payment request again
		prh.getNfcEventHandler().handleMessage(NfcEvent.CONNECTION_LOST, null);
		prh.getNfcEventHandler().handleMessage(NfcEvent.INITIALIZED, null);
		handleMessage = messageHandler.handleMessage(data, sendLater);
		assertNull(handleMessage);
		assertNotNull(sendLaterBytes);
		pm = new PaymentMessage().bytes(sendLaterBytes);
		assertFalse(pm.isError());
		sendLaterBytes = null;
		
		paymentRequestPayer = DecoderFactory.decode(PaymentRequest.class, pm.payload());
		assertEquals(userInfosPayer.getUsername(), paymentRequestPayer.getUsernamePayer());
		assertEquals(userInfosPayee.getUsername(), paymentRequestPayer.getUsernamePayee());
		assertEquals(paymentInfos.getCurrency().getCode(), paymentRequestPayer.getCurrency().getCode());
		assertEquals(paymentInfos.getAmount(), paymentRequestPayer.getAmount());

		assertEquals(1, persistencyHandler.getList().size());

		// receive payment response
		PaymentRequest paymentRequestPayee = new PaymentRequest(userInfosPayee.getPKIAlgorithm(), userInfosPayee.getKeyNumber(), paymentRequestPayer.getUsernamePayer(), userInfosPayee.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount(), paymentRequestPayer.getTimestamp());
		assertTrue(paymentRequestPayer.requestsIdentic(paymentRequestPayee));
		paymentRequestPayee.sign(userInfosPayer.getPrivateKey());
		ServerPaymentRequest spr = new ServerPaymentRequest(paymentRequestPayer, paymentRequestPayee);

		ServerPaymentRequest decode = DecoderFactory.decode(ServerPaymentRequest.class, spr.encode());
		PaymentRequest paymentRequestPayer2 = decode.getPaymentRequestPayer();

		PaymentResponse pr = new PaymentResponse(PKIAlgorithm.DEFAULT, 1, ServerResponseStatus.SUCCESS, null, paymentRequestPayer2.getUsernamePayer(), paymentRequestPayer2.getUsernamePayee(), paymentRequestPayer2.getCurrency(), paymentRequestPayer2.getAmount(), paymentRequestPayer2.getTimestamp());
		pr.sign(keyPairServer.getPrivate());
		ServerPaymentResponse spr2 = new ServerPaymentResponse(pr);
		byte[] encode = spr2.encode();

		ServerPaymentResponse serverPaymentResponse = DecoderFactory.decode(ServerPaymentResponse.class, encode);
		byte[] encode2 = serverPaymentResponse.getPaymentResponsePayer().encode();
		
		pm = new PaymentMessage().payee().payload(encode2);

		byte[] handleMessage2 = messageHandler.handleMessage(pm.bytes(), sendLater);
		assertNull(sendLaterBytes);
		
		PaymentMessage pm2 = new PaymentMessage().bytes(handleMessage2);
		assertFalse(pm2.isError());
		assertEquals(1, pm2.payload().length);
		assertEquals(PaymentRequestHandler.ACK[0], pm2.payload()[0]);

		assertEquals(0, persistencyHandler.getList().size());
		
		assertEquals(3, states.size());
		State state = states.get(0);
		assertEquals(PaymentEvent.INITIALIZED, state.event);
		state = states.get(1);
		assertEquals(PaymentEvent.INITIALIZED, state.event);
		state = states.get(2);
		assertEquals(PaymentEvent.SUCCESS, state.event);
		assertNotNull(state.object);
		assertTrue(state.object instanceof PaymentResponse);
		PaymentResponse pr1 = (PaymentResponse) state.object;
		assertEquals(userInfosPayer.getUsername(), pr1.getUsernamePayer());
		assertEquals(userInfosPayee.getUsername(), pr1.getUsernamePayee());
	}
	
	@Test
	public void testPaymentRequestHandler_Payer_ServerRefuses() throws Exception {
		/*
		 * Simulates server refuses the payment for any reason
		 */
		reset();

		KeyPair keyPairPayer = TestUtils.generateKeyPair();
		UserInfos userInfosPayer = new UserInfos("payer", keyPairPayer.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		PaymentInfos paymentInfos = new PaymentInfos(Currency.BTC, 1);
		
		KeyPair keyPairPayee = TestUtils.generateKeyPair();
		KeyPair keyPairServer = TestUtils.generateKeyPair();
		UserInfos userInfosPayee = new UserInfos("payee", keyPairPayee.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		ServerInfos serverInfos = new ServerInfos(keyPairServer.getPublic());
		
		PaymentRequestHandler prh = new PaymentRequestHandler(hostActivity, paymentEventHandler, userInfosPayee, serverInfos, defaultUserPrompt, persistencyHandler);
		MessageHandler messageHandler = prh.getMessageHandler();
		prh.getNfcEventHandler().handleMessage(NfcEvent.INITIALIZED, null);
		
		// receive request to send username
		PaymentMessage pm = new PaymentMessage().payer().payload(new byte[] { 0x00 });
		assertTrue(pm.isPayer());
		
		byte[] handleMessage = messageHandler.handleMessage(pm.bytes(), sendLater);
		assertNull(sendLaterBytes);
		pm = new PaymentMessage().bytes(handleMessage);
		
		assertFalse(pm.isError());
		
		// receive payment response from server
		PaymentRequest paymentRequestPayer = new PaymentRequest(userInfosPayer.getPKIAlgorithm(), userInfosPayer.getKeyNumber(), userInfosPayer.getUsername(), userInfosPayee.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount(), paymentInfos.getTimestamp());
		paymentRequestPayer.sign(userInfosPayer.getPrivateKey());
		ServerPaymentRequest spr = new ServerPaymentRequest(paymentRequestPayer);
		
		ServerPaymentRequest decode = DecoderFactory.decode(ServerPaymentRequest.class, spr.encode());
		PaymentRequest paymentRequestPayer2 = decode.getPaymentRequestPayer();
		
		String reason = "any reason";
		PaymentResponse pr = new PaymentResponse(PKIAlgorithm.DEFAULT, 1, ServerResponseStatus.FAILURE, reason, paymentRequestPayer2.getUsernamePayer(), paymentRequestPayer2.getUsernamePayee(), paymentRequestPayer2.getCurrency(), paymentRequestPayer2.getAmount(), paymentRequestPayer2.getTimestamp());
		pr.sign(keyPairServer.getPrivate());
		ServerPaymentResponse spr2 = new ServerPaymentResponse(pr);
		byte[] encode = spr2.encode();
		
		ServerPaymentResponse serverPaymentResponse = DecoderFactory.decode(ServerPaymentResponse.class, encode);
		byte[] encode2 = serverPaymentResponse.getPaymentResponsePayer().encode();
		
		byte[] data = new PaymentMessage().payer().payload(encode2).bytes();
		
		byte[] handleMessage2 = messageHandler.handleMessage(data, sendLater);
		assertNull(sendLaterBytes);
		
		PaymentMessage pm2 = new PaymentMessage().bytes(handleMessage2);
		assertFalse(pm2.isError());
		assertEquals(1, pm2.payload().length);
		assertEquals(PaymentRequestHandler.ACK[0], pm2.payload()[0]);
		
		assertEquals(2, states.size());
		State state = states.get(0);
		assertEquals(PaymentEvent.INITIALIZED, state.event);
		state = states.get(1);
		assertEquals(PaymentEvent.ERROR, state.event);
		assertNotNull(state.object);
		assertTrue(state.object instanceof PaymentError);
		PaymentError err = (PaymentError) state.object;
		assertEquals(PaymentError.SERVER_REFUSED, err);
	}
	
	@Test
	public void testPaymentRequestHandler_Payer_Success() throws Exception {
		/*
		 * Simulates a successful payment
		 */
		reset();

		KeyPair keyPairPayer = TestUtils.generateKeyPair();
		UserInfos userInfosPayer = new UserInfos("payer", keyPairPayer.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		PaymentInfos paymentInfos = new PaymentInfos(Currency.BTC, 1);
		
		KeyPair keyPairPayee = TestUtils.generateKeyPair();
		KeyPair keyPairServer = TestUtils.generateKeyPair();
		UserInfos userInfosPayee = new UserInfos("payee", keyPairPayee.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		ServerInfos serverInfos = new ServerInfos(keyPairServer.getPublic());
		
		PaymentRequestHandler prh = new PaymentRequestHandler(hostActivity, paymentEventHandler, userInfosPayee, serverInfos, defaultUserPrompt, persistencyHandler);
		MessageHandler messageHandler = prh.getMessageHandler();
		prh.getNfcEventHandler().handleMessage(NfcEvent.INITIALIZED, null);
		
		// receive request to send username
		PaymentMessage pm = new PaymentMessage().payer().payload(new byte[] { 0x00 });
		assertTrue(pm.isPayer());
		
		byte[] handleMessage = messageHandler.handleMessage(pm.bytes(), sendLater);
		assertNull(sendLaterBytes);
		pm = new PaymentMessage().bytes(handleMessage);
		
		assertFalse(pm.isError());
		
		// receive payment response
		PaymentRequest paymentRequestPayer = new PaymentRequest(userInfosPayer.getPKIAlgorithm(), userInfosPayer.getKeyNumber(), userInfosPayer.getUsername(), userInfosPayee.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount(), paymentInfos.getTimestamp());
		paymentRequestPayer.sign(userInfosPayer.getPrivateKey());
		ServerPaymentRequest spr = new ServerPaymentRequest(paymentRequestPayer);
		
		ServerPaymentRequest decode = DecoderFactory.decode(ServerPaymentRequest.class, spr.encode());
		PaymentRequest paymentRequestPayer2 = decode.getPaymentRequestPayer();
		
		PaymentResponse pr = new PaymentResponse(PKIAlgorithm.DEFAULT, 1, ServerResponseStatus.SUCCESS, null, paymentRequestPayer2.getUsernamePayer(), paymentRequestPayer2.getUsernamePayee(), paymentRequestPayer2.getCurrency(), paymentRequestPayer2.getAmount(), paymentRequestPayer2.getTimestamp());
		pr.sign(keyPairServer.getPrivate());
		ServerPaymentResponse spr2 = new ServerPaymentResponse(pr);
		byte[] encode = spr2.encode();
		
		ServerPaymentResponse serverPaymentResponse = DecoderFactory.decode(ServerPaymentResponse.class, encode);
		byte[] encode2 = serverPaymentResponse.getPaymentResponsePayer().encode();
		
		byte[] data = new PaymentMessage().payer().payload(encode2).bytes();
		
		byte[] handleMessage2 = messageHandler.handleMessage(data, sendLater);
		assertNull(sendLaterBytes);
		
		PaymentMessage pm2 = new PaymentMessage().bytes(handleMessage2);
		assertFalse(pm2.isError());
		assertEquals(1, pm2.payload().length);
		assertEquals(PaymentRequestHandler.ACK[0], pm2.payload()[0]);
		
		assertEquals(2, states.size());
		State state = states.get(0);
		assertEquals(PaymentEvent.INITIALIZED, state.event);
		state = states.get(1);
		assertEquals(PaymentEvent.SUCCESS, state.event);
		assertNotNull(state.object);
		assertTrue(state.object instanceof PaymentResponse);
		PaymentResponse pr1 = (PaymentResponse) state.object;
		assertEquals(userInfosPayer.getUsername(), pr1.getUsernamePayer());
		assertEquals(userInfosPayee.getUsername(), pr1.getUsernamePayee());
	}
	
	@Test
	public void testPaymentRequestHandler_IllegalVersion() throws Exception {
		/*
		 * Simulates server refuses the payment for any reason
		 */
		reset();
		
		KeyPair keyPairPayee = TestUtils.generateKeyPair();
		UserInfos userInfosPayee = new UserInfos("seller", keyPairPayee.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		PaymentInfos paymentInfos = new PaymentInfos(Currency.BTC, 1);
		
		KeyPair keyPairPayer = TestUtils.generateKeyPair();
		KeyPair keyPairServer = TestUtils.generateKeyPair();
		UserInfos userInfosPayer = new UserInfos("buyer", keyPairPayer.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		ServerInfos serverInfos = new ServerInfos(keyPairServer.getPublic());
		
		PaymentRequestHandler prh = new PaymentRequestHandler(hostActivity, paymentEventHandler, userInfosPayer, serverInfos, defaultUserPrompt, persistencyHandler);
		MessageHandler messageHandler = prh.getMessageHandler();
		prh.getNfcEventHandler().handleMessage(NfcEvent.INITIALIZED, null);
		
		// receive payment request - will fail because of the unknown version
		InitMessagePayee initMessage = new InitMessagePayee(userInfosPayee.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount());
		byte header = (byte) 0xC0; // 11000000
		PaymentMessage pm = new PaymentMessage().bytes(new byte[] { header });
		pm = pm.payee().payload(initMessage.encode());
		assertTrue(pm.isPayee());
		
		byte[] handleMessage = messageHandler.handleMessage(pm.bytes(), sendLater);
		PaymentMessage response = new PaymentMessage().bytes(handleMessage);
		assertTrue(response.isError());
		
		assertNull(sendLaterBytes);
		
		assertEquals(2, states.size());
		State state = states.get(0);
		assertEquals(PaymentEvent.INITIALIZED, state.event);
		state = states.get(1);
		assertEquals(PaymentEvent.ERROR, state.event);
		assertNotNull(state.object);
		assertTrue(state.object instanceof PaymentError);
		PaymentError err = (PaymentError) state.object;
		assertEquals(PaymentError.INCOMPATIBLE_VERSIONS, err);
	}
	
}
