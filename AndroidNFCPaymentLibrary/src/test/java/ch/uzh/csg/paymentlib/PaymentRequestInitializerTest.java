package ch.uzh.csg.paymentlib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.security.KeyPair;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
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
import ch.uzh.csg.nfclib.NfcEvent;
import ch.uzh.csg.nfclib.transceiver.NfcTransceiver;
import ch.uzh.csg.paymentlib.PaymentRequestInitializer.PaymentType;
import ch.uzh.csg.paymentlib.container.PaymentInfos;
import ch.uzh.csg.paymentlib.container.ServerInfos;
import ch.uzh.csg.paymentlib.container.UserInfos;
import ch.uzh.csg.paymentlib.messages.PaymentError;
import ch.uzh.csg.paymentlib.messages.PaymentMessage;
import ch.uzh.csg.paymentlib.testutils.TestUtils;
import ch.uzh.csg.paymentlib.util.Config;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Log.class)
public class PaymentRequestInitializerTest {
	
	private Activity hostActivity = Mockito.mock(Activity.class);
	
	private boolean paymentError = false;
	private Object paymentErrorObject = null;
	private boolean paymentForwardToServer = false;
	private Object paymentForwardToServerObject = null;
	private boolean paymentServerResponseTimeout = false;
	private Object paymentServerResponseTimeoutObject = null;
	private boolean paymentSuccess = false;
	private Object paymentSuccessObject = null;
	private boolean paymentOtherEvent = false;
	private Object paymentOtherEventObject = null;
	
	private KeyPair keyPairServer;
	private PaymentRequestInitializer pri;
	
	private boolean serverRefuse;
	private boolean serverTimeout;
	
	private void reset() {
		paymentError = false;
		paymentErrorObject = null;
		paymentForwardToServer = false;
		paymentForwardToServerObject = null;
		paymentServerResponseTimeout = false;
		paymentServerResponseTimeoutObject = null;
		paymentSuccess = false;
		paymentSuccessObject = null;
		paymentOtherEvent = false;
		paymentOtherEventObject = null;
		
		serverRefuse = false;
		serverTimeout = false;
	}
	
	private PaymentEventInterface paymentEventHandler = new PaymentEventInterface() {
		
		@Override
		public void handleMessage(PaymentEvent event, Object object) {
			switch (event) {
			case ERROR:
				paymentError = true;
				paymentErrorObject = object;
				break;
			case FORWARD_TO_SERVER:
				if (serverTimeout) {
					try {
						Thread.sleep(Config.SERVER_CALL_TIMEOUT+100);
					} catch (InterruptedException e) {
					}
					break;
				}
				
				paymentForwardToServer = true;
				paymentForwardToServerObject = object;
				if (serverRefuse) {
					try {
						assertTrue(object instanceof byte[]);
						ServerPaymentRequest decode = DecoderFactory.decode(ServerPaymentRequest.class, (byte[]) object);
						PaymentRequest paymentRequestPayer = decode.getPaymentRequestPayer();
						
						String reason = "I don't like you";
						PaymentResponse pr = new PaymentResponse(PKIAlgorithm.DEFAULT, 1, ServerResponseStatus.FAILURE, reason, paymentRequestPayer.getUsernamePayer(), paymentRequestPayer.getUsernamePayee(), paymentRequestPayer.getCurrency(), paymentRequestPayer.getAmount(), paymentRequestPayer.getTimestamp());
						pr.sign(keyPairServer.getPrivate());
						ServerPaymentResponse spr = new ServerPaymentResponse(pr);
						byte[] encode = spr.encode();
						pri.onServerResponse(encode);
					} catch (Exception e) {
						assertTrue(false);
					}
				} else {
					try {
						assertTrue(object instanceof byte[]);
						ServerPaymentRequest decode = DecoderFactory.decode(ServerPaymentRequest.class, (byte[]) object);
						PaymentRequest paymentRequestPayer = decode.getPaymentRequestPayer();
						
						PaymentResponse pr = new PaymentResponse(PKIAlgorithm.DEFAULT, 1, ServerResponseStatus.SUCCESS, null, paymentRequestPayer.getUsernamePayer(), paymentRequestPayer.getUsernamePayee(), paymentRequestPayer.getCurrency(), paymentRequestPayer.getAmount(), paymentRequestPayer.getTimestamp());
						pr.sign(keyPairServer.getPrivate());
						ServerPaymentResponse spr = new ServerPaymentResponse(pr);
						byte[] encode = spr.encode();
						pri.onServerResponse(encode);
					} catch (Exception e) {
						assertTrue(false);
					}
				}
				break;
			case NO_SERVER_RESPONSE:
				paymentServerResponseTimeout = true;
				paymentServerResponseTimeoutObject = object;
				break;
			case SUCCESS:
				paymentSuccess = true;
				paymentSuccessObject = object;
				break;
			default:
				paymentOtherEvent = true;
				paymentOtherEventObject = object;
				break;
			}
		}
	};
	
	@Before
	public void before() {
		PowerMockito.mockStatic(Log.class);
		PowerMockito.when(Log.d(Mockito.anyString(), Mockito.anyString())).then(new Answer<Integer>() {
			@Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
	            System.err.println(Arrays.toString(invocation.getArguments()));
	            return 0;
            }
		});
	}
	
	@Test
	public void testPaymentRequestInitializer_Payee_PayerRefuses() throws Exception {
		/*
		 * Simulates payee rejects the payment.
		 */
		reset();
		
		KeyPair keyPairPayee = TestUtils.generateKeyPair();
		KeyPair keyPairServer = TestUtils.generateKeyPair();
		
		UserInfos userInfos = new UserInfos("seller", keyPairPayee.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		PaymentInfos paymentInfos = new PaymentInfos(Currency.BTC, 1);
		ServerInfos serverInfos = new ServerInfos(keyPairServer.getPublic());
		
		NfcTransceiver transceiver = mock(NfcTransceiver.class);
		
		final PaymentRequestInitializer pri = new PaymentRequestInitializer(hostActivity, transceiver, paymentEventHandler, userInfos, paymentInfos, serverInfos, PaymentType.REQUEST_PAYMENT);
		
		Stubber stubber = doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.MESSAGE_SENT, null);
				
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage((byte[]) arguments[0]);
				assertEquals(PaymentMessage.DEFAULT, pm.getStatus());
				
				byte[] response = new PaymentMessage(PaymentMessage.ERROR, new byte[] { PaymentError.PAYER_REFUSED.getCode() }).getData();
				assertNotNull(response);
				pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.MESSAGE_RECEIVED, response);
				return null;
			}
		});
		stubber.when(transceiver).transceive(any(byte[].class));
		
		Stubber stubber2 = doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				// do nothing
				return null;
			}
			
		});
		stubber2.when(transceiver).disable(hostActivity);
		
		//start test case manually, since this would be started on an nfc contact!
		pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.INITIALIZED, null);
		
		
		verify(transceiver).transceive(any(byte[].class));
		verify(transceiver).disable(any(Activity.class));
		
		assertFalse(paymentForwardToServer);
		assertNull(paymentForwardToServerObject);
		assertFalse(paymentServerResponseTimeout);
		assertNull(paymentServerResponseTimeoutObject);
		assertFalse(paymentSuccess);
		assertNull(paymentSuccessObject);
		assertFalse(paymentOtherEvent);
		assertNull(paymentOtherEventObject);
		
		assertTrue(paymentError);
		assertTrue(paymentErrorObject instanceof PaymentError);
		PaymentError err = (PaymentError) paymentErrorObject;
		assertEquals(PaymentError.PAYER_REFUSED.getCode(), err.getCode());
	}
	
	@Test
	public void testPaymentRequestInitializer_Payee_PayerModifiesRequest() throws Exception {
		/*
		 * Simulates payer modifies the payment request (e.g. the amount).
		 */
		reset();
		
		KeyPair keyPairPayee = TestUtils.generateKeyPair();
		KeyPair keyPairServer = TestUtils.generateKeyPair();
		
		UserInfos userInfosPayee = new UserInfos("seller", keyPairPayee.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		PaymentInfos paymentInfos = new PaymentInfos(Currency.BTC, 1);
		ServerInfos serverInfos = new ServerInfos(keyPairServer.getPublic());
		
		KeyPair keyPairPayer = TestUtils.generateKeyPair();
		final UserInfos userInfosPayer = new UserInfos("buyer", keyPairPayer.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		
		NfcTransceiver transceiver = mock(NfcTransceiver.class);
		
		final PaymentRequestInitializer pri = new PaymentRequestInitializer(hostActivity, transceiver, paymentEventHandler, userInfosPayee, paymentInfos, serverInfos, PaymentType.REQUEST_PAYMENT);
		
		Stubber stubber = doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.MESSAGE_SENT, null);
				
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage((byte[]) arguments[0]);
				assertEquals(PaymentMessage.DEFAULT, pm.getStatus());
				
				InitMessagePayee initMessage = DecoderFactory.decode(InitMessagePayee.class, pm.getPayload());
				
				PaymentRequest pr = new PaymentRequest(userInfosPayer.getPKIAlgorithm(), userInfosPayer.getKeyNumber(), userInfosPayer.getUsername(), initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount()+1, System.currentTimeMillis());
				pr.sign(userInfosPayer.getPrivateKey());
				
				byte[] response = new PaymentMessage(PaymentMessage.DEFAULT, pr.encode()).getData();
				assertNotNull(response);
				
				pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.MESSAGE_RECEIVED, response);
				return null;
			}
		}).doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.MESSAGE_SENT, null);
				
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage((byte[]) arguments[0]);
				assertEquals(PaymentMessage.ERROR, pm.getStatus());
				
				byte[] response = new PaymentMessage(PaymentMessage.ERROR, new byte[] { pm.getPayload()[0] }).getData();
				assertNotNull(response);
				
				pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.MESSAGE_RECEIVED, response);
				return null;
			}
		});
		stubber.when(transceiver).transceive(any(byte[].class));
		
		Stubber stubber2 = doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				// do nothing
				return null;
			}
			
		});
		stubber2.when(transceiver).disable(hostActivity);
		
		//start test case manually, since this would be started on an nfc contact!
		pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.INITIALIZED, null);
		
		
		verify(transceiver, times(2)).transceive(any(byte[].class));
		verify(transceiver).disable(any(Activity.class));
		
		assertFalse(paymentForwardToServer);
		assertNull(paymentForwardToServerObject);
		assertFalse(paymentServerResponseTimeout);
		assertNull(paymentServerResponseTimeoutObject);
		assertFalse(paymentSuccess);
		assertNull(paymentSuccessObject);
		assertFalse(paymentOtherEvent);
		assertNull(paymentOtherEventObject);
		
		assertTrue(paymentError);
		assertTrue(paymentErrorObject instanceof PaymentError);
		PaymentError err = (PaymentError) paymentErrorObject;
		assertEquals(PaymentError.REQUESTS_NOT_IDENTIC.getCode(), err.getCode());
	}
	
	@Test
	public void testPaymentRequestInitializer_Payee_ServerRefuses() throws Exception {
		/*
		 * Simulates server refuses the payment for any reason
		 */
		reset();
		serverRefuse = true;
		
		KeyPair keyPairPayee = TestUtils.generateKeyPair();
		keyPairServer = TestUtils.generateKeyPair();
		
		UserInfos userInfosPayee = new UserInfos("seller", keyPairPayee.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		PaymentInfos paymentInfos = new PaymentInfos(Currency.BTC, 1);
		ServerInfos serverInfos = new ServerInfos(keyPairServer.getPublic());
		
		KeyPair keyPairPayer = TestUtils.generateKeyPair();
		final UserInfos userInfosPayer = new UserInfos("buyer", keyPairPayer.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		
		NfcTransceiver transceiver = mock(NfcTransceiver.class);
		
		pri = new PaymentRequestInitializer(hostActivity, transceiver, paymentEventHandler, userInfosPayee, paymentInfos, serverInfos, PaymentType.REQUEST_PAYMENT);
		
		Stubber stubber = doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.MESSAGE_SENT, null);
				
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage((byte[]) arguments[0]);
				assertEquals(PaymentMessage.DEFAULT, pm.getStatus());
				
				InitMessagePayee initMessage = DecoderFactory.decode(InitMessagePayee.class, pm.getPayload());
				
				PaymentRequest pr = new PaymentRequest(userInfosPayer.getPKIAlgorithm(), userInfosPayer.getKeyNumber(), userInfosPayer.getUsername(), initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount(), System.currentTimeMillis());
				pr.sign(userInfosPayer.getPrivateKey());
				
				byte[] response = new PaymentMessage(PaymentMessage.DEFAULT, pr.encode()).getData();
				assertNotNull(response);
				
				pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.MESSAGE_RECEIVED, response);
				return null;
			}
		}).doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.MESSAGE_SENT, null);
				
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage((byte[]) arguments[0]);
				assertEquals(PaymentMessage.DEFAULT, pm.getStatus());
				
				PaymentResponse pr = DecoderFactory.decode(PaymentResponse.class, pm.getPayload());
				assertNotNull(pr);
				assertEquals(ServerResponseStatus.FAILURE.getCode(), pr.getStatus().getCode());
				
				byte[] response = new PaymentMessage(PaymentMessage.DEFAULT, PaymentRequestHandler.ACK).getData();
				assertNotNull(response);
				
				pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.MESSAGE_RECEIVED, response);
				return null;
			}
		});
		stubber.when(transceiver).transceive(any(byte[].class));
		
		Stubber stubber2 = doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				// do nothing
				return null;
			}
			
		});
		stubber2.when(transceiver).disable(hostActivity);
		
		//start test case manually, since this would be started on an nfc contact!
		pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.INITIALIZED, null);
		
		
		verify(transceiver, times(2)).transceive(any(byte[].class));
		verify(transceiver).disable(any(Activity.class));
		
		assertFalse(paymentServerResponseTimeout);
		assertNull(paymentServerResponseTimeoutObject);
		assertFalse(paymentSuccess);
		assertNull(paymentSuccessObject);
		assertFalse(paymentOtherEvent);
		assertNull(paymentOtherEventObject);
		
		assertTrue(paymentForwardToServer);
		assertNotNull(paymentForwardToServerObject);
		assertTrue(paymentError);
		assertTrue(paymentErrorObject instanceof PaymentError);
		PaymentError err = (PaymentError) paymentErrorObject;
		assertEquals(PaymentError.SERVER_REFUSED.getCode(), err.getCode());
	}
	
	@Test
	public void testPaymentRequestInitializer_Success() throws Exception {
		/*
		 * Simulates a successful payment
		 */
		reset();
		serverRefuse = false;
		
		KeyPair keyPairPayee = TestUtils.generateKeyPair();
		keyPairServer = TestUtils.generateKeyPair();
		
		UserInfos userInfosPayee = new UserInfos("seller", keyPairPayee.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		PaymentInfos paymentInfos = new PaymentInfos(Currency.BTC, 1);
		ServerInfos serverInfos = new ServerInfos(keyPairServer.getPublic());
		
		KeyPair keyPairPayer = TestUtils.generateKeyPair();
		final UserInfos userInfosPayer = new UserInfos("buyer", keyPairPayer.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		
		NfcTransceiver transceiver = mock(NfcTransceiver.class);
		
		pri = new PaymentRequestInitializer(hostActivity, transceiver, paymentEventHandler, userInfosPayee, paymentInfos, serverInfos, PaymentType.REQUEST_PAYMENT);
		
		Stubber stubber = doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.MESSAGE_SENT, null);
				
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage((byte[]) arguments[0]);
				assertEquals(PaymentMessage.DEFAULT, pm.getStatus());
				
				InitMessagePayee initMessage = DecoderFactory.decode(InitMessagePayee.class, pm.getPayload());
				
				PaymentRequest pr = new PaymentRequest(userInfosPayer.getPKIAlgorithm(), userInfosPayer.getKeyNumber(), userInfosPayer.getUsername(), initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount(), System.currentTimeMillis());
				pr.sign(userInfosPayer.getPrivateKey());
				
				byte[] response = new PaymentMessage(PaymentMessage.DEFAULT, pr.encode()).getData();
				assertNotNull(response);
				
				pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.MESSAGE_RECEIVED, response);
				return null;
			}
		}).doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.MESSAGE_SENT, null);
				
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage((byte[]) arguments[0]);
				assertEquals(PaymentMessage.DEFAULT, pm.getStatus());
				
				PaymentResponse pr = DecoderFactory.decode(PaymentResponse.class, pm.getPayload());
				assertNotNull(pr);
				assertEquals(ServerResponseStatus.SUCCESS.getCode(), pr.getStatus().getCode());
				
				byte[] response = new PaymentMessage(PaymentMessage.DEFAULT, PaymentRequestHandler.ACK).getData();
				assertNotNull(response);
				
				pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.MESSAGE_RECEIVED, response);
				return null;
			}
		});
		stubber.when(transceiver).transceive(any(byte[].class));
		
		Stubber stubber2 = doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				// do nothing
				return null;
			}
			
		});
		stubber2.when(transceiver).disable(hostActivity);
		
		//start test case manually, since this would be started on an nfc contact!
		pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.INITIALIZED, null);
		
		
		verify(transceiver, times(2)).transceive(any(byte[].class));
		verify(transceiver).disable(any(Activity.class));
		
		assertFalse(paymentServerResponseTimeout);
		assertNull(paymentServerResponseTimeoutObject);
		assertFalse(paymentError);
		assertNull(paymentErrorObject);
		assertFalse(paymentOtherEvent);
		assertNull(paymentOtherEventObject);
		
		assertTrue(paymentForwardToServer);
		assertNotNull(paymentForwardToServerObject);
		assertTrue(paymentSuccess);
		assertNotNull(paymentSuccessObject);
		assertTrue(paymentSuccessObject instanceof PaymentResponse);
		PaymentResponse pr = (PaymentResponse) paymentSuccessObject;
		assertEquals(userInfosPayer.getUsername(), pr.getUsernamePayer());
		assertEquals(userInfosPayee.getUsername(), pr.getUsernamePayee());
	}
	
	@Test
	public void testPaymentRequestInitializer_ServerCallTimeout() throws Exception {
		/*
		 * Simulates a successful payment
		 */
		reset();
		serverTimeout = true;
		
		KeyPair keyPairPayee = TestUtils.generateKeyPair();
		keyPairServer = TestUtils.generateKeyPair();
		
		UserInfos userInfosPayee = new UserInfos("seller", keyPairPayee.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		PaymentInfos paymentInfos = new PaymentInfos(Currency.BTC, 1);
		ServerInfos serverInfos = new ServerInfos(keyPairServer.getPublic());
		
		KeyPair keyPairPayer = TestUtils.generateKeyPair();
		final UserInfos userInfosPayer = new UserInfos("buyer", keyPairPayer.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		
		NfcTransceiver transceiver = mock(NfcTransceiver.class);
		
		pri = new PaymentRequestInitializer(hostActivity, transceiver, paymentEventHandler, userInfosPayee, paymentInfos, serverInfos, PaymentType.REQUEST_PAYMENT);
		
		Stubber stubber = doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.MESSAGE_SENT, null);
				
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage((byte[]) arguments[0]);
				assertEquals(PaymentMessage.DEFAULT, pm.getStatus());
				
				InitMessagePayee initMessage = DecoderFactory.decode(InitMessagePayee.class, pm.getPayload());
				
				PaymentRequest pr = new PaymentRequest(userInfosPayer.getPKIAlgorithm(), userInfosPayer.getKeyNumber(), userInfosPayer.getUsername(), initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount(), System.currentTimeMillis());
				pr.sign(userInfosPayer.getPrivateKey());
				
				byte[] response = new PaymentMessage(PaymentMessage.DEFAULT, pr.encode()).getData();
				assertNotNull(response);
				
				pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.MESSAGE_RECEIVED, response);
				return null;
			}
		}).doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.MESSAGE_SENT, null);
				
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage((byte[]) arguments[0]);
				assertEquals(PaymentMessage.ERROR, pm.getStatus());
				assertEquals(1, pm.getPayloadLength());
				assertEquals(PaymentError.NO_SERVER_RESPONSE.getCode(), pm.getPayload()[0]);
				
				byte[] response = new PaymentMessage(PaymentMessage.ERROR, new byte[] { pm.getPayload()[0] }).getData();
				assertNotNull(response);
				
				pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.MESSAGE_RECEIVED, response);
				return null;
			}
		});
		stubber.when(transceiver).transceive(any(byte[].class));
		
		Stubber stubber2 = doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				// do nothing
				return null;
			}
			
		});
		stubber2.when(transceiver).disable(hostActivity);
		
		//start test case manually, since this would be started on an nfc contact!
		pri.getNfcEventHandlerRequest().handleMessage(NfcEvent.INITIALIZED, null);
		
		Thread.sleep(Config.SERVER_CALL_TIMEOUT+500);
		
		verify(transceiver, times(2)).transceive(any(byte[].class));
		verify(transceiver).disable(any(Activity.class));
		
		assertFalse(paymentError);
		assertNull(paymentErrorObject);
		assertFalse(paymentOtherEvent);
		assertNull(paymentOtherEventObject);
		assertFalse(paymentForwardToServer);
		assertNull(paymentForwardToServerObject);
		assertFalse(paymentSuccess);
		assertNull(paymentSuccessObject);
		
		assertTrue(paymentServerResponseTimeout);
		assertNull(paymentServerResponseTimeoutObject);
	}
	
}
