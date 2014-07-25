package ch.uzh.csg.paymentlib;

import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.util.Log;
import ch.uzh.csg.mbps.customserialization.DecoderFactory;
import ch.uzh.csg.mbps.customserialization.InitMessagePayee;
import ch.uzh.csg.mbps.customserialization.PaymentRequest;
import ch.uzh.csg.mbps.customserialization.PaymentResponse;
import ch.uzh.csg.mbps.customserialization.ServerPaymentRequest;
import ch.uzh.csg.mbps.customserialization.ServerPaymentResponse;
import ch.uzh.csg.mbps.customserialization.exceptions.NotSignedException;
import ch.uzh.csg.nfclib.NfcInitiator;
import ch.uzh.csg.nfclib.NfcLibException;
import ch.uzh.csg.nfclib.events.INfcEventHandler;
import ch.uzh.csg.nfclib.events.NfcEvent;
import ch.uzh.csg.paymentlib.container.PaymentInfos;
import ch.uzh.csg.paymentlib.container.ServerInfos;
import ch.uzh.csg.paymentlib.container.UserInfos;
import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;
import ch.uzh.csg.paymentlib.exceptions.UnknownPaymentErrorException;
import ch.uzh.csg.paymentlib.messages.PaymentError;
import ch.uzh.csg.paymentlib.messages.PaymentMessage;
import ch.uzh.csg.paymentlib.persistency.IPersistencyHandler;
import ch.uzh.csg.paymentlib.persistency.PersistedPaymentRequest;
import ch.uzh.csg.paymentlib.util.Config;

/**
 * This class is responsible for initializing payment requests. Based on the
 * provided {@link PaymentType}, the payee or the payer initializes the payment
 * which results in a slightly different protocol.
 * 
 * This class handles the underlying NFC and the messages which need to be sent.
 * 
 * If the server response is not returned within a given threshold (see
 * {@link Config}) then the {@link PaymentEvent}.NO_SERVER_RESPONSE is fired.
 * All other events from {@link PaymentEvent} are also fired appropriately
 * during the communication.
 * 
 * @author Jeton Memeti
 * 
 */
public class PaymentRequestInitializer implements IServerResponseListener {
	
	public static final String TAG = "ch.uzh.csg.paymentlib.PaymentRequestInitializer";
	
	/**
	 * Defines a Payment type. If the payee (or seller) initiates a payment
	 * request, the type REQUEST_PAYMENT has to be chosen. If the payer (or
	 * buyer) initiates the payment request, the type SEND_PAYMENT has to be
	 * chosen.
	 */
	public enum PaymentType {
		REQUEST_PAYMENT,
		SEND_PAYMENT;
	}
	
	private PaymentType paymentType;
	
	private Activity activity;
	private volatile IPaymentEventHandler paymentEventHandler;
	
	private UserInfos userInfos;
	private ServerInfos serverInfos;
	private PaymentInfos paymentInfos;
	private IPersistencyHandler persistencyHandler;
	
	private volatile NfcInitiator nfcTransceiver;
	private int nofMessages = 0;
	private boolean aborted = false;
	private boolean disabled = false;
	
	private PersistedPaymentRequest persistedPaymentRequest;
	
	private ExecutorService executorService;
	private ServerTimeoutTask timeoutTask;
	
	/**
	 * Instantiates a new Payment Request Initializer in order to conduct a
	 * payment with another device over NFC.
	 * 
	 * @param activity
	 *            the current application's activity, needed to hook the NFC
	 * @param paymentEventHandler
	 *            the event handler, which will be notified on any
	 *            {@link PaymentEvent}
	 * @param userInfos
	 *            the user information of the user initiating the payment
	 *            request
	 * @param paymentInfos
	 *            the specific payment information
	 * @param serverInfos
	 *            the server information
	 * @param persistencyHandler
	 *            the object responsible for writing
	 *            {@link PersistedPaymentRequest} to the device's local storage.
	 *            This is needed to avoid unintended double transactions. This
	 *            is only needed if {@link PaymentType} == SEND_PAYMENT.
	 *            Otherwise you may pass null.
	 * @param type
	 *            the {@link PaymentType}
	 * @throws IllegalArgumentException
	 *             if any parameter is not valid (e.g., null)
	 * @throws NfcLibException
	 *             if the underlying NFC feature cannot be used for any reason
	 */
	public PaymentRequestInitializer(Activity activity, IPaymentEventHandler paymentEventHandler, UserInfos userInfos, PaymentInfos paymentInfos, ServerInfos serverInfos, IPersistencyHandler persistencyHandler, PaymentType type) throws IllegalArgumentException,  NfcLibException {
		this(activity, null, paymentEventHandler, userInfos, paymentInfos, serverInfos, persistencyHandler, type);
	}
	
	/*
	 * This constructor is only for test purposes, in order to mock the
	 * NfcTransceiver. For productive use the public constructor, otherwise the
	 * NFC will not work.
	 */
	protected PaymentRequestInitializer(Activity activity, NfcInitiator nfcTransceiver, IPaymentEventHandler paymentEventHandler, UserInfos userInfos, PaymentInfos paymentInfos, ServerInfos serverInfos, IPersistencyHandler persistencyHandler, PaymentType type) throws IllegalArgumentException, NfcLibException {
		checkParameters(activity, paymentEventHandler, userInfos, paymentInfos, serverInfos, persistencyHandler, type);
		
		this.paymentType = type;
		this.activity = activity;
		this.paymentEventHandler = paymentEventHandler;
		this.userInfos = userInfos;
		this.serverInfos = serverInfos;
		this.paymentInfos = paymentInfos;
		this.persistencyHandler = persistencyHandler;
		
		this.executorService = Executors.newSingleThreadExecutor();
		
		initPayment(nfcTransceiver);
	}

	private void checkParameters(Activity activity, IPaymentEventHandler paymentEventHandler, UserInfos userInfos, PaymentInfos paymentInfos, ServerInfos serverInfos, IPersistencyHandler persistencyHandler, PaymentType type) throws IllegalArgumentException {
		if (activity == null)
			throw new IllegalArgumentException("The activity cannot be null.");
		
		if (paymentEventHandler == null)
			throw new IllegalArgumentException("The payment event handler cannot be null.");
		
		if (userInfos == null)
			throw new IllegalArgumentException("The user infos cannot be null.");
		
		if (paymentInfos == null)
			throw new IllegalArgumentException("The payment infos cannot be null.");
		
		if (serverInfos == null)
			throw new IllegalArgumentException("The server infos cannot be null.");
		
		if (type == null)
			throw new IllegalArgumentException("The payment type cannot be null.");
		
		if (type == PaymentType.SEND_PAYMENT && persistencyHandler == null)
			throw new IllegalArgumentException("The persistency handler cannot be null.");
	}
	
	private void initPayment(NfcInitiator nfcTransceiver) throws NfcLibException {
		INfcEventHandler nfcEventHandler;
		if (this.paymentType == PaymentType.REQUEST_PAYMENT)
			nfcEventHandler = nfcEventHandlerRequest;
		else
			nfcEventHandler = nfcEventHandlerSend;
		
		if (nfcTransceiver != null) {
			this.nfcTransceiver = nfcTransceiver;
		} else {
			this.nfcTransceiver = new NfcInitiator(nfcEventHandler, activity, userInfos.getUserId());
			
			if (Config.DEBUG)
				Log.d(TAG, "Init and enable transceiver");
				
			this.nfcTransceiver.enable(activity);
		}
	}
	
	/**
	 * Replaces the payment infos provided in the constructor with the new ones.
	 * 
	 * @param newPaymentInfos
	 *            the new payment infos
	 */
	public void setPaymentInfos(PaymentInfos newPaymentInfos) {
		if (newPaymentInfos == null)
			throw new java.lang.IllegalArgumentException("The payment infos can't be null.");
		
		this.paymentInfos = newPaymentInfos;
	}
	
	/**
	 * Disables the NFC capability bound to this activity. This has to be called
	 * once you want to finish using the NFC streaming or the payment process is
	 * finished and is not meant to restart again.
	 * 
	 * If you call this method and stay in the same activity, then the Android
	 * Beam jumps in (see
	 * http://developer.android.com/guide/topics/connectivity/nfc/nfc.html#p2p).
	 */
	public void disable() {
		if (Config.DEBUG)
			Log.d(TAG, "Disable NFC");
		
		if (!disabled) {
			nfcTransceiver.disable(activity);
			disabled = true;
		}
	}
	
	/**
	 * Enables the NFC so that messages can be exchanged.
	 */
	public void enableNfc() {
		nfcTransceiver.enableNfc();
	}
	
	/**
	 * Soft disables the NFC to prevent devices such as the Samsung Galaxy Note
	 * 3 (other devices may show the same behavior!) to restart the protocol
	 * after having send the last message!
	 * 
	 * This should be called after a successful communication. Once you want to
	 * restart the NFC capability, call enableNFC.
	 */
	public void disableNfc() {
		nfcTransceiver.disableNfc();
	}
	
	private void reset() {
		if (Config.DEBUG)
			Log.d(TAG, "Resetting states");
		
		nofMessages = 0;
		persistedPaymentRequest = null;
		
		if (disabled) {
			nfcTransceiver.enable(activity);
			disabled = false;
		}
	}
	
	private void startTimeoutTask() {
		terminateTimeoutTask();
		
		if (Config.DEBUG)
			Log.d(TAG, "Starting new timeout task");
		
		timeoutTask = new ServerTimeoutTask();
		executorService.submit(timeoutTask);
	}

	private void terminateTimeoutTask() {
		if (timeoutTask != null) {
			if (Config.DEBUG)
				Log.d(TAG, "Terminating timeout task");
			
			timeoutTask.terminate();
			timeoutTask = null;
		}
	}
	
	private synchronized void sendError(PaymentError err) {
		aborted = true;
		
		if (Config.DEBUG)
			Log.d(TAG, "Sending error: "+err);
		
		nfcTransceiver.transceive(new PaymentMessage().error().payload(new byte[] { err.getCode() }).bytes());
		paymentEventHandler.handleMessage(PaymentEvent.ERROR, err, null);
		reset();
	}
	
	/*
	 * only for test purposes
	 */
	protected INfcEventHandler getNfcEventHandler() {
		if (paymentType == PaymentType.REQUEST_PAYMENT)
			return nfcEventHandlerRequest;
		else if (paymentType == PaymentType.SEND_PAYMENT)
			return nfcEventHandlerSend;
		else
			return null;
	}
	
	private INfcEventHandler nfcEventHandlerRequest = new INfcEventHandler() {
		
		@Override
		public void handleMessage(NfcEvent event, Object object) {
			if (Config.DEBUG)
				Log.d(TAG, "Received NfcEvent: "+event);
			
			switch (event) {
			case INIT_FAILED:
				paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.INIT_FAILED, null);
				reset();
				break;
			case FATAL_ERROR:
				paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.UNEXPECTED_ERROR, null);
				reset();
				break;
			case CONNECTION_LOST:
				break;
			case INITIALIZED:
				aborted = false;
				
				paymentEventHandler.handleMessage(PaymentEvent.INITIALIZED, null, null);
				nofMessages = 0;
				try {
					InitMessagePayee initMessage = new InitMessagePayee(userInfos.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount());
					
					if (Config.DEBUG)
						Log.d(TAG, "Sending init message payment request");
					
					nfcTransceiver.transceive(new PaymentMessage().payee().payload(initMessage.encode()).bytes());
				} catch (Exception e) {
					sendError(PaymentError.UNEXPECTED_ERROR);
				}
				break;
			case MESSAGE_RECEIVED:
				if (aborted) {
					break;
				}
				
				nofMessages++;
				if (object == null || !(object instanceof byte[])) {
					sendError(PaymentError.UNEXPECTED_ERROR);
					break;
				}
				PaymentMessage response = new PaymentMessage().bytes((byte[]) object);
				
				if (response.version() > PaymentMessage.getSupportedVersion()) {
					if (Config.DEBUG)
						Log.d(TAG, "excepted PaymentMessage version "+PaymentMessage.getSupportedVersion()+" but was "+response.version());
					
					sendError(PaymentError.INCOMPATIBLE_VERSIONS);
					break;
				}
				
				if (response.isError()) {
					if (Config.DEBUG)
						Log.d(TAG, "Received PaymentMessage ERROR");
					
					PaymentError paymentError = null;
					if (response.payload() != null && response.payload().length > 0) {
						try {
							paymentError = PaymentError.getPaymentError(response.payload()[0]);
						} catch (UnknownPaymentErrorException e) {
						}
					}
					
					paymentEventHandler.handleMessage(PaymentEvent.ERROR, paymentError, null);
					reset();
					break;
				}
				
				switch (nofMessages) {
				case 1:
					try {
						if (Config.DEBUG)
							Log.d(TAG, "Received signed payment request from payer");
						
						PaymentRequest paymentRequestPayer = DecoderFactory.decode(PaymentRequest.class, response.payload());
						
						PaymentRequest paymentRequestPayee;
						if (paymentInfos.getInputCurrency() == null) {
							paymentRequestPayee = new PaymentRequest(userInfos.getPKIAlgorithm(), userInfos.getKeyNumber(), paymentRequestPayer.getUsernamePayer(), userInfos.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount(), paymentRequestPayer.getTimestamp());
						} else {
							paymentRequestPayee = new PaymentRequest(userInfos.getPKIAlgorithm(), userInfos.getKeyNumber(), paymentRequestPayer.getUsernamePayer(), userInfos.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount(), paymentInfos.getInputCurrency(), paymentInfos.getInputAmount(), paymentRequestPayer.getTimestamp());
						}
						
						if (!paymentRequestPayer.requestsIdentic(paymentRequestPayee)) {
							Log.e(TAG, "The received payment request does not correspond to the payment request sent. Aborted the payment process.");
							sendError(PaymentError.REQUESTS_NOT_IDENTIC);
						} else {
							paymentRequestPayee.sign(userInfos.getPrivateKey());
							ServerPaymentRequest spr = new ServerPaymentRequest(paymentRequestPayer, paymentRequestPayee);
							
							if (Config.DEBUG)
								Log.d(TAG, "About to forward the payment request to the server");
							
							startTimeoutTask();
							paymentEventHandler.handleMessage(PaymentEvent.FORWARD_TO_SERVER, spr.encode(), PaymentRequestInitializer.this);
						}
					} catch (Exception e) {
						Log.wtf(TAG, e);
						sendError(PaymentError.UNEXPECTED_ERROR);
					}
					break;
				case 2:
					if (Config.DEBUG)
						Log.d(TAG, "Received ACK");
					
					reset();
					break;
				}
				break;
			}
		}
	};
	
	private INfcEventHandler nfcEventHandlerSend = new INfcEventHandler() {
		
		@Override
		public void handleMessage(NfcEvent event, Object object) {	
			if (Config.DEBUG)
				Log.d(TAG, "Received NfcEvent: "+event);
			
			switch (event) {
			case INIT_FAILED:
				paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.INIT_FAILED, null);
				reset();
				break;
			case FATAL_ERROR:
				paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.UNEXPECTED_ERROR, null);
				reset();
				break;
			case CONNECTION_LOST:
				break;
			case INITIALIZED:
				aborted = false;
				
				paymentEventHandler.handleMessage(PaymentEvent.INITIALIZED, null, null);
				nofMessages = 0;
				try {
					if (Config.DEBUG)
						Log.d(TAG, "Request the payee's username");
					
					//send empty message, we just need the payee's username
					nfcTransceiver.transceive(new PaymentMessage().payer().payload(new byte[] { 0x00 }).bytes());
				} catch (Exception e) {
					sendError(PaymentError.UNEXPECTED_ERROR);
				}
				break;
			case MESSAGE_RECEIVED:
				if (aborted)
					break;
				
				nofMessages++;
				if (object == null || !(object instanceof byte[])) {
					sendError(PaymentError.UNEXPECTED_ERROR);
					break;
				}
				PaymentMessage response = new PaymentMessage().bytes((byte[]) object);
				
				if (response.version() > PaymentMessage.getSupportedVersion()) {
					if (Config.DEBUG)
						Log.d(TAG, "excepted PaymentMessage version "+PaymentMessage.getSupportedVersion()+" but was "+response.version());
					
					sendError(PaymentError.INCOMPATIBLE_VERSIONS);
					break;
				}
				
				if (response.isError()) {
					if (Config.DEBUG)
						Log.d(TAG, "Received PaymentMessage ERROR");
					
					PaymentError paymentError = null;
					if (response.payload() != null && response.payload().length > 0) {
						try {
							paymentError = PaymentError.getPaymentError(response.payload()[0]);
						} catch (UnknownPaymentErrorException e) {
						}
					}
					
					paymentEventHandler.handleMessage(PaymentEvent.ERROR, paymentError, null);
					reset();
					break;
				}
				
				switch (nofMessages) {
				case 1:
					if (Config.DEBUG)
						Log.d(TAG, "Received the payee's username");
					
					String usernamePayee = new String(response.payload(), Charset.forName("UTF-8"));
					
					try {
						if (persistedPaymentRequest == null
								|| !persistedPaymentRequest.getUsername().equals(usernamePayee)
								|| persistedPaymentRequest.getCurrency().getCode() != paymentInfos.getCurrency().getCode()
								|| persistedPaymentRequest.getAmount() != paymentInfos.getAmount()) {
							// this is a new session
							persistedPaymentRequest = persistencyHandler.getPersistedPaymentRequest(usernamePayee, paymentInfos.getCurrency(), paymentInfos.getAmount());
							if (persistedPaymentRequest == null) {
								if (Config.DEBUG)
									Log.d(TAG, "Creating new payment request");
								
								persistedPaymentRequest = new PersistedPaymentRequest(usernamePayee, paymentInfos.getCurrency(), paymentInfos.getAmount(), System.currentTimeMillis());
								persistencyHandler.addPersistedPaymentRequest(persistedPaymentRequest);
							} else {
								if (Config.DEBUG)
									Log.d(TAG, "Loaded payment request from internal storage (previous payment request did not receive any server response)");
							}
						} else {
							/*
							 * this is a payment resume (the user took his
							 * device away to accept/reject the payment)
							 */
							if (Config.DEBUG)
								Log.d(TAG, "Payment resume after reconnection");
						}
						
						PaymentRequest paymentRequestPayer = null;
						if (paymentInfos.getInputCurrency() == null) {
							paymentRequestPayer = new PaymentRequest(userInfos.getPKIAlgorithm(), userInfos.getKeyNumber(), userInfos.getUsername(), persistedPaymentRequest.getUsername(), persistedPaymentRequest.getCurrency(), persistedPaymentRequest.getAmount(), persistedPaymentRequest.getTimestamp());
						} else {
							paymentRequestPayer = new PaymentRequest(userInfos.getPKIAlgorithm(), userInfos.getKeyNumber(), userInfos.getUsername(), persistedPaymentRequest.getUsername(), persistedPaymentRequest.getCurrency(), persistedPaymentRequest.getAmount(), paymentInfos.getInputCurrency(), paymentInfos.getInputAmount(), persistedPaymentRequest.getTimestamp());
						}
						paymentRequestPayer.sign(userInfos.getPrivateKey());
						
						ServerPaymentRequest spr = new ServerPaymentRequest(paymentRequestPayer);
						
						if (Config.DEBUG)
							Log.d(TAG, "About to forward the payment request to the server");
						
						startTimeoutTask();
						paymentEventHandler.handleMessage(PaymentEvent.FORWARD_TO_SERVER, spr.encode(), PaymentRequestInitializer.this);
					} catch (Exception e) {
						Log.wtf(TAG, e);
						sendError(PaymentError.UNEXPECTED_ERROR);
					}
					break;
				case 2:
					if (Config.DEBUG)
						Log.d(TAG, "Received ACK");
					
					reset();
					break;
				}
				break;
			}
		}
	};
	
	@Override
	public void onServerResponse(ServerPaymentResponse serverPaymentResponse) {
		terminateTimeoutTask();
		
		if (aborted)
			return;
		
		if (Config.DEBUG)
			Log.d(TAG, "Received the server response");
		
		PaymentResponse toProcess = null;
		PaymentResponse toForward = null;
			
		switch (paymentType) {
		case REQUEST_PAYMENT:
			if (serverPaymentResponse.getPaymentResponsePayee() != null) {
				toProcess = serverPaymentResponse.getPaymentResponsePayee();
				toForward = serverPaymentResponse.getPaymentResponsePayer();
			} else {
				toProcess = serverPaymentResponse.getPaymentResponsePayer();
				toForward = toProcess;
			}
			break;
		case SEND_PAYMENT:
			toProcess = serverPaymentResponse.getPaymentResponsePayer();
			if (serverPaymentResponse.getPaymentResponsePayee() != null) {
				toForward = serverPaymentResponse.getPaymentResponsePayee();
			} else {
				toForward = toProcess;
			}
			break;
		}
			
		boolean signatureValid = false;
		try {
			signatureValid = toProcess.verify(serverInfos.getPublicKey());
			if (!signatureValid) {
				Log.e(TAG, "The signature of the server response is not valid! This might be a Man-In-The-Middle attack, where someone manipulated the server response.");
				sendError(PaymentError.NO_SERVER_RESPONSE);
				return;
			}
		} catch (Exception e) {
			Log.wtf(TAG, e);
			sendError(PaymentError.NO_SERVER_RESPONSE);
			return;
		}
		
		if (persistedPaymentRequest != null)
			persistencyHandler.deletePersistedPaymentRequest(persistedPaymentRequest);
		
		try {
			byte[] encode = toForward.encode();
			
			if (Config.DEBUG)
				Log.d(TAG, "Forwarding the payment response over NFC");
			
			switch (paymentType) {
			case REQUEST_PAYMENT:
				nfcTransceiver.transceive(new PaymentMessage().payee().payload(encode).bytes());
				break;
			case SEND_PAYMENT:
				nfcTransceiver.transceive(new PaymentMessage().payer().payload(encode).bytes());
				break;
			}
		} catch (NotSignedException e) {
			Log.wtf(TAG, e);
			sendError(PaymentError.NO_SERVER_RESPONSE);
			return;
		}
		
		switch (toProcess.getStatus()) {
		case FAILURE:
			if (Config.DEBUG)
				Log.d(TAG, "The server refused the payment");
			
			paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.SERVER_REFUSED, null);
			break;
		case SUCCESS:
			if (Config.DEBUG)
				Log.d(TAG, "The payment request was successful");
			
			paymentEventHandler.handleMessage(PaymentEvent.SUCCESS, toProcess, null);
			break;
		case DUPLICATE_REQUEST:
			if (Config.DEBUG)
				Log.d(TAG, "This payment request has already been accepted by the server before");
			
			paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.DUPLICATE_REQUEST, null);
			break;
		}
	}
	
	private class ServerTimeoutTask implements Runnable {
		private final CountDownLatch latch = new CountDownLatch(1);
		private final long startTime = System.currentTimeMillis();
		
		public void terminate() {
			latch.countDown();
		}
		
		public void run() {
			try {
				if (latch.await(Config.SERVER_CALL_TIMEOUT, TimeUnit.MILLISECONDS)) {
					//countdown reached 0, we wanted to terminate this thread
				} else {
					//waiting time elapsed
					if (Config.DEBUG)
						Log.d(TAG, "Server response timeout (timeout)");
					
					sendError(PaymentError.NO_SERVER_RESPONSE);
				}
			} catch (InterruptedException e) {
				//the current thread has been interrupted
				long now = System.currentTimeMillis();
				if (now - startTime >= Config.SERVER_CALL_TIMEOUT) {
					if (Config.DEBUG)
						Log.d(TAG, "Server response timeout (interrupted)");
					
					sendError(PaymentError.NO_SERVER_RESPONSE);
				}
			}
		}
	}

}
