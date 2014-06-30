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

import java.nio.charset.Charset;
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
import ch.uzh.csg.nfclib.NfcEvent.Type;
import ch.uzh.csg.nfclib.NfcInitiator;
import ch.uzh.csg.paymentlib.PaymentRequestInitializer.PaymentType;
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
	
	private PersistencyHandler persistencyHandler;
	
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
		
		persistencyHandler = new PersistencyHandler();
		
		serverRefuse = false;
		serverTimeout = false;
	}
	
	private IPaymentEventHandler paymentEventHandler = new IPaymentEventHandler() {
		
		@Override
		public void handleMessage(PaymentEvent event, Object object) {
			switch (event) {
			case ERROR:
				paymentError = true;
				paymentErrorObject = object;
				break;
			case FORWARD_TO_SERVER:
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

		@Override
		public void handleMessage(PaymentEvent event, Object object, IServerResponseListener caller) {
			switch (event) {
			case ERROR:
			case NO_SERVER_RESPONSE:
			case SUCCESS:
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
						pri.onServerResponse(new ServerPaymentResponse(pr));
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
						pri.onServerResponse(new ServerPaymentResponse(pr));
					} catch (Exception e) {
						assertTrue(false);
					}
				}
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
		
		NfcInitiator transceiver = mock(NfcInitiator.class);
		
		final PaymentRequestInitializer pri = new PaymentRequestInitializer(hostActivity, transceiver, paymentEventHandler, userInfos, paymentInfos, serverInfos, persistencyHandler, PaymentType.REQUEST_PAYMENT);
		
		Stubber stubber = doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage().bytes((byte[]) arguments[0]);
				assertEquals(PaymentMessage.DEFAULT, pm.header());
				
				byte[] response = new PaymentMessage().error().payload(new byte[] { PaymentError.PAYER_REFUSED.getCode() }).bytes();
				assertNotNull(response);
				pri.getNfcEventHandler().handleMessage(Type.MESSAGE_RECEIVED, response);
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
		pri.getNfcEventHandler().handleMessage(Type.INITIALIZED, null);
		
		assertEquals(0, persistencyHandler.getList().size());
		
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
		
		NfcInitiator transceiver = mock(NfcInitiator.class);
		
		final PaymentRequestInitializer pri = new PaymentRequestInitializer(hostActivity, transceiver, paymentEventHandler, userInfosPayee, paymentInfos, serverInfos, persistencyHandler, PaymentType.REQUEST_PAYMENT);
		
		Stubber stubber = doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage().bytes((byte[]) arguments[0]);
				assertEquals(PaymentMessage.DEFAULT, pm.header());
				
				InitMessagePayee initMessage = DecoderFactory.decode(InitMessagePayee.class, pm.payload());
				
				PaymentRequest pr = new PaymentRequest(userInfosPayer.getPKIAlgorithm(), userInfosPayer.getKeyNumber(), userInfosPayer.getUsername(), initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount()+1, System.currentTimeMillis());
				pr.sign(userInfosPayer.getPrivateKey());
				
				byte[] response = new PaymentMessage().payload(pr.encode()).bytes();
				assertNotNull(response);
				
				pri.getNfcEventHandler().handleMessage(Type.MESSAGE_RECEIVED, response);
				return null;
			}
		}).doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage().bytes((byte[]) arguments[0]);
				assertEquals(PaymentMessage.ERROR, pm.header());
				
				byte[] response = new PaymentMessage().error().payload(new byte[] { pm.payload()[0] }).bytes();
				assertNotNull(response);
				
				pri.getNfcEventHandler().handleMessage(Type.MESSAGE_RECEIVED, response);
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
		pri.getNfcEventHandler().handleMessage(Type.INITIALIZED, null);
		
		assertEquals(0, persistencyHandler.getList().size());
		
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
		
		NfcInitiator transceiver = mock(NfcInitiator.class);
		
		pri = new PaymentRequestInitializer(hostActivity, transceiver, paymentEventHandler, userInfosPayee, paymentInfos, serverInfos, persistencyHandler, PaymentType.REQUEST_PAYMENT);
		
		Stubber stubber = doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage().bytes((byte[]) arguments[0]);
				assertEquals(PaymentMessage.DEFAULT, pm.header());
				
				InitMessagePayee initMessage = DecoderFactory.decode(InitMessagePayee.class, pm.payload());
				
				PaymentRequest pr = new PaymentRequest(userInfosPayer.getPKIAlgorithm(), userInfosPayer.getKeyNumber(), userInfosPayer.getUsername(), initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount(), System.currentTimeMillis());
				pr.sign(userInfosPayer.getPrivateKey());
				
				byte[] response = new PaymentMessage().payload(pr.encode()).bytes();
				assertNotNull(response);
				
				pri.getNfcEventHandler().handleMessage(Type.MESSAGE_RECEIVED, response);
				return null;
			}
		}).doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage().bytes((byte[]) arguments[0]);
				assertEquals(PaymentMessage.DEFAULT, pm.header());
				
				PaymentResponse pr = DecoderFactory.decode(PaymentResponse.class, pm.payload());
				assertNotNull(pr);
				assertEquals(ServerResponseStatus.FAILURE.getCode(), pr.getStatus().getCode());
				
				byte[] response = new PaymentMessage().payload(PaymentRequestHandler.ACK).bytes();
				assertNotNull(response);
				
				pri.getNfcEventHandler().handleMessage(Type.MESSAGE_RECEIVED, response);
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
		pri.getNfcEventHandler().handleMessage(Type.INITIALIZED, null);
		
		assertEquals(0, persistencyHandler.getList().size());
		
		verify(transceiver, times(2)).transceive(any(byte[].class));
		//TODO jeton: needed?
//		verify(transceiver).disable(any(Activity.class));
		
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
	public void testPaymentRequestInitializer_Payee_Success() throws Exception {
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
		
		NfcInitiator transceiver = mock(NfcInitiator.class);
		
		pri = new PaymentRequestInitializer(hostActivity, transceiver, paymentEventHandler, userInfosPayee, paymentInfos, serverInfos, persistencyHandler, PaymentType.REQUEST_PAYMENT);
		
		Stubber stubber = doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage().bytes((byte[]) arguments[0]);
				assertEquals(PaymentMessage.DEFAULT, pm.header());
				
				InitMessagePayee initMessage = DecoderFactory.decode(InitMessagePayee.class, pm.payload());
				
				PaymentRequest pr = new PaymentRequest(userInfosPayer.getPKIAlgorithm(), userInfosPayer.getKeyNumber(), userInfosPayer.getUsername(), initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount(), System.currentTimeMillis());
				pr.sign(userInfosPayer.getPrivateKey());
				
				byte[] response = new PaymentMessage().payload(pr.encode()).bytes();
				assertNotNull(response);
				
				pri.getNfcEventHandler().handleMessage(Type.MESSAGE_RECEIVED, response);
				return null;
			}
		}).doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage().bytes((byte[]) arguments[0]);
				assertEquals(PaymentMessage.DEFAULT, pm.header());
				
				PaymentResponse pr = DecoderFactory.decode(PaymentResponse.class, pm.payload());
				assertNotNull(pr);
				assertEquals(ServerResponseStatus.SUCCESS.getCode(), pr.getStatus().getCode());
				
				byte[] response = new PaymentMessage().payload(PaymentRequestHandler.ACK).bytes();
				assertNotNull(response);
				
				pri.getNfcEventHandler().handleMessage(Type.MESSAGE_RECEIVED, response);
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
		pri.getNfcEventHandler().handleMessage(Type.INITIALIZED, null);
		
		assertEquals(0, persistencyHandler.getList().size());
		
		verify(transceiver, times(2)).transceive(any(byte[].class));
		//TODO jeton: needed?
//		verify(transceiver).disable(any(Activity.class));
		
		//assure that the timeout is not thrown
		Thread.sleep(Config.SERVER_CALL_TIMEOUT+500);
		
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
	public void testPaymentRequestInitializer_Payee_ServerCallTimeout() throws Exception {
		/*
		 * Simulates a server timeout
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
		
		NfcInitiator transceiver = mock(NfcInitiator.class);
		
		pri = new PaymentRequestInitializer(hostActivity, transceiver, paymentEventHandler, userInfosPayee, paymentInfos, serverInfos, persistencyHandler, PaymentType.REQUEST_PAYMENT);
		
		Stubber stubber = doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage().bytes((byte[]) arguments[0]);
				assertEquals(PaymentMessage.DEFAULT, pm.header());
				
				InitMessagePayee initMessage = DecoderFactory.decode(InitMessagePayee.class, pm.payload());
				
				PaymentRequest pr = new PaymentRequest(userInfosPayer.getPKIAlgorithm(), userInfosPayer.getKeyNumber(), userInfosPayer.getUsername(), initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount(), System.currentTimeMillis());
				pr.sign(userInfosPayer.getPrivateKey());
				
				byte[] response = new PaymentMessage().payload(pr.encode()).bytes();
				assertNotNull(response);
				
				pri.getNfcEventHandler().handleMessage(Type.MESSAGE_RECEIVED, response);
				return null;
			}
		}).doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage().bytes((byte[]) arguments[0]);
				assertEquals(PaymentMessage.ERROR, pm.header());
				assertEquals(1, pm.payload().length);
				assertEquals(PaymentError.NO_SERVER_RESPONSE.getCode(), pm.payload()[0]);
				
				byte[] response = new PaymentMessage().error().payload(new byte[] { pm.payload()[0] }).bytes();
				assertNotNull(response);
				
				pri.getNfcEventHandler().handleMessage(Type.MESSAGE_RECEIVED, response);
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
		pri.getNfcEventHandler().handleMessage(Type.INITIALIZED, null);
		
		Thread.sleep(Config.SERVER_CALL_TIMEOUT+500);
		
		assertEquals(0, persistencyHandler.getList().size());
		
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
	
	@Test
	public void testPaymentRequestInitializer_Payer_ServerRefuses() throws Exception {
		/*
		 * Simulates server refuses the payment for any reason
		 */
		reset();
		serverRefuse = true;
		
		KeyPair keyPairPayer = TestUtils.generateKeyPair();
		keyPairServer = TestUtils.generateKeyPair();
		
		UserInfos userInfosPayer = new UserInfos("payer", keyPairPayer.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		PaymentInfos paymentInfos = new PaymentInfos(Currency.BTC, 1, System.currentTimeMillis());
		ServerInfos serverInfos = new ServerInfos(keyPairServer.getPublic());
		
		KeyPair keyPairPayee = TestUtils.generateKeyPair();
		final UserInfos userInfosPayee = new UserInfos("payee", keyPairPayee.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		
		NfcInitiator transceiver = mock(NfcInitiator.class);
		
		pri = new PaymentRequestInitializer(hostActivity, transceiver, paymentEventHandler, userInfosPayer, paymentInfos, serverInfos, persistencyHandler, PaymentType.SEND_PAYMENT);
		
		Stubber stubber = doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage().bytes((byte[]) arguments[0]);
				assertFalse(pm.isError());
				assertTrue(pm.isPayer());
				
				byte[] bytes = userInfosPayee.getUsername().getBytes(Charset.forName("UTF-8"));
				byte[] response = new PaymentMessage().payee().payload(bytes).bytes();
				assertNotNull(response);
				
				pri.getNfcEventHandler().handleMessage(Type.MESSAGE_RECEIVED, response);
				return null;
			}
		}).doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage().bytes((byte[]) arguments[0]);
				assertFalse(pm.isError());
				assertTrue(pm.isPayer());
				
				PaymentResponse pr = DecoderFactory.decode(PaymentResponse.class, pm.payload());
				assertNotNull(pr);
				assertEquals(ServerResponseStatus.FAILURE.getCode(), pr.getStatus().getCode());
				
				byte[] response = new PaymentMessage().payload(PaymentRequestHandler.ACK).bytes();
				assertNotNull(response);
				
				pri.getNfcEventHandler().handleMessage(Type.MESSAGE_RECEIVED, response);
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
		pri.getNfcEventHandler().handleMessage(Type.INITIALIZED, null);
		
		assertEquals(0, persistencyHandler.getList().size());
		
		verify(transceiver, times(2)).transceive(any(byte[].class));
		//TODO jeton: needed?
//		verify(transceiver).disable(any(Activity.class));
		
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
	public void testPaymentRequestInitializer_Payer_Success() throws Exception {
		/*
		 * Simulates a successful payment
		 */
		reset();
		serverRefuse = false;
		
		KeyPair keyPairPayer = TestUtils.generateKeyPair();
		keyPairServer = TestUtils.generateKeyPair();
		
		UserInfos userInfosPayer = new UserInfos("seller", keyPairPayer.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		PaymentInfos paymentInfos = new PaymentInfos(Currency.BTC, 1, System.currentTimeMillis());
		ServerInfos serverInfos = new ServerInfos(keyPairServer.getPublic());
		
		KeyPair keyPairPayee = TestUtils.generateKeyPair();
		final UserInfos userInfosPayee = new UserInfos("buyer", keyPairPayee.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		
		NfcInitiator transceiver = mock(NfcInitiator.class);
		
		pri = new PaymentRequestInitializer(hostActivity, transceiver, paymentEventHandler, userInfosPayer, paymentInfos, serverInfos, persistencyHandler, PaymentType.SEND_PAYMENT);
		
		Stubber stubber = doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage().bytes((byte[]) arguments[0]);
				assertFalse(pm.isError());
				assertTrue(pm.isPayer());
				
				byte[] bytes = userInfosPayee.getUsername().getBytes(Charset.forName("UTF-8"));
				byte[] response = new PaymentMessage().payee().payload(bytes).bytes();
				assertNotNull(response);
				
				pri.getNfcEventHandler().handleMessage(Type.MESSAGE_RECEIVED, response);
				return null;
			}
		}).doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage().bytes((byte[]) arguments[0]);
				assertFalse(pm.isError());
				assertTrue(pm.isPayer());
				
				PaymentResponse pr = DecoderFactory.decode(PaymentResponse.class, pm.payload());
				assertNotNull(pr);
				assertEquals(ServerResponseStatus.SUCCESS.getCode(), pr.getStatus().getCode());
				
				byte[] response = new PaymentMessage().payload(PaymentRequestHandler.ACK).bytes();
				assertNotNull(response);
				
				pri.getNfcEventHandler().handleMessage(Type.MESSAGE_RECEIVED, response);
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
		pri.getNfcEventHandler().handleMessage(Type.INITIALIZED, null);
		
		assertEquals(0, persistencyHandler.getList().size());
		
		verify(transceiver, times(2)).transceive(any(byte[].class));
		//TODO jeton: needed?
//		verify(transceiver).disable(any(Activity.class));
		
		//assure that the timeout is not thrown
		Thread.sleep(Config.SERVER_CALL_TIMEOUT+500);
		
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
	public void testPaymentRequestInitializer_Payer_ServerCallTimeout() throws Exception {
		/*
		 * Simulates a server timeout
		 */
		reset();
		serverTimeout = true;
		
		KeyPair keyPairPayer = TestUtils.generateKeyPair();
		keyPairServer = TestUtils.generateKeyPair();
		
		UserInfos userInfosPayer = new UserInfos("seller", keyPairPayer.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		PaymentInfos paymentInfos = new PaymentInfos(Currency.BTC, 1, System.currentTimeMillis());
		ServerInfos serverInfos = new ServerInfos(keyPairServer.getPublic());
		
		KeyPair keyPairPayee = TestUtils.generateKeyPair();
		final UserInfos userInfosPayee = new UserInfos("buyer", keyPairPayee.getPrivate(), PKIAlgorithm.DEFAULT, 1);
		
		NfcInitiator transceiver = mock(NfcInitiator.class);
		
		pri = new PaymentRequestInitializer(hostActivity, transceiver, paymentEventHandler, userInfosPayer, paymentInfos, serverInfos, persistencyHandler, PaymentType.SEND_PAYMENT);
		
		Stubber stubber = doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage().bytes((byte[]) arguments[0]);
				assertFalse(pm.isError());
				assertTrue(pm.isPayer());
				
				byte[] bytes = userInfosPayee.getUsername().getBytes(Charset.forName("UTF-8"));
				byte[] response = new PaymentMessage().payee().payload(bytes).bytes();
				assertNotNull(response);
				
				pri.getNfcEventHandler().handleMessage(Type.MESSAGE_RECEIVED, response);
				return null;
			}
		}).doAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				Object[] arguments = invocation.getArguments();
				
				PaymentMessage pm = new PaymentMessage().bytes((byte[]) arguments[0]);
				assertTrue(pm.isError());
				assertEquals(1, pm.payload().length);
				assertEquals(PaymentError.NO_SERVER_RESPONSE.getCode(), pm.payload()[0]);
				
				byte[] response = new PaymentMessage().error().payload(new byte[] { pm.payload()[0] }).bytes();
				assertNotNull(response);
				
				pri.getNfcEventHandler().handleMessage(Type.MESSAGE_RECEIVED, response);
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
		pri.getNfcEventHandler().handleMessage(Type.INITIALIZED, null);
		
		Thread.sleep(Config.SERVER_CALL_TIMEOUT+500);
		
		assertEquals(1, persistencyHandler.getList().size());
		
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
