package ch.uzh.csg.paymentlib;

import java.nio.charset.Charset;
import java.util.Arrays;
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
import ch.uzh.csg.nfclib.ISendLater;
import ch.uzh.csg.nfclib.ITransceiveHandler;
import ch.uzh.csg.nfclib.NfcResponder;
import ch.uzh.csg.nfclib.events.INfcEventHandler;
import ch.uzh.csg.nfclib.events.NfcEvent;
import ch.uzh.csg.nfclib.hce.HostApduServiceNfcLib;
import ch.uzh.csg.paymentlib.PaymentRequestInitializer.PaymentType;
import ch.uzh.csg.paymentlib.container.ServerInfos;
import ch.uzh.csg.paymentlib.container.UserInfos;
import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;
import ch.uzh.csg.paymentlib.messages.PaymentError;
import ch.uzh.csg.paymentlib.messages.PaymentMessage;
import ch.uzh.csg.paymentlib.persistency.IPersistencyHandler;
import ch.uzh.csg.paymentlib.persistency.PersistedPaymentRequest;
import ch.uzh.csg.paymentlib.util.Config;

/**
 * This class is the counterpart of {@link PaymentRequestInitializer} and
 * handles a payment request coming from another NFC device.
 * 
 * This class handles the underlying NFC and the messages which need to be
 * processed and returned.
 * 
 * If the server response is not returned within a given threshold (see
 * {@link Config}) then the {@link PaymentEvent}.NO_SERVER_RESPONSE is fired.
 * All other events from {@link PaymentEvent} are also fired appropriately
 * during the communication.
 * 
 * @author Jeton Memeti
 * 
 */
public class PaymentRequestHandler {
	
	public static final String TAG = "ch.uzh.csg.paymentlib.PaymentRequestHandler";
	
	/**
	 * The ack message to be returned if the payment finished successfully.
	 */
	public static final byte[] ACK = new byte[] { (byte) 0xAC };
	
	private IPaymentEventHandler paymentEventHandler;
	private UserInfos userInfos;
	private ServerInfos serverInfos;
	private IUserPromptPaymentRequest userPrompt;
	private IPersistencyHandler persistencyHandler;
	private MessageHandler messageHandler;
	
	private boolean connected = false;
	
	private int nofMessages = 0;
	private boolean aborted = false;
	
	private ExecutorService executorService;
	private ServerTimeoutTask timeoutTask;
	private boolean startTimeoutTask = false;
	
	private PersistedPaymentRequest persistedPaymentRequest;
	
	/**
	 * Instantiates a new payment request handler, which handles incoming
	 * payment requests (irrespective of the {@link PaymentType}).
	 * 
	 * @param activity
	 *            the current application's activity, needed to hook the NFC
	 * @param paymentEventHandler
	 *            the event handler, which will be notified on any
	 *            {@link PaymentEvent}
	 * @param userInfos
	 *            the user information of the current user
	 * @param serverInfos
	 *            the server information
	 * @param userPrompt
	 *            the object responsible for prompting the user if he accepts or
	 *            rejects the payment and returning the answer
	 * @param persistencyHandler
	 *            the instance responsible for writing
	 *            {@link PersistedPaymentRequest} to the device's local storage
	 * @throws IllegalArgumentException
	 *             if any paramter is not valid (e.g., null)
	 */
	public PaymentRequestHandler(Activity activity, IPaymentEventHandler paymentEventHandler, UserInfos userInfos, ServerInfos serverInfos, IUserPromptPaymentRequest userPrompt, IPersistencyHandler persistencyHandler) throws IllegalArgumentException {
		checkParameters(activity, paymentEventHandler, userInfos, serverInfos, userPrompt, persistencyHandler);
		
		this.paymentEventHandler = paymentEventHandler;
		this.userInfos = userInfos;
		this.serverInfos = serverInfos;
		this.userPrompt = userPrompt;
		this.persistencyHandler = persistencyHandler;
		this.messageHandler = new MessageHandler();
		
		this.executorService = Executors.newSingleThreadExecutor();
		
		NfcResponder c = new NfcResponder(nfcEventHandler, messageHandler);
		HostApduServiceNfcLib.init(c);
	}
	
	private void checkParameters(Activity activity, IPaymentEventHandler paymentEventHandler, UserInfos userInfos, ServerInfos serverInfos, IUserPromptPaymentRequest userPrompt, IPersistencyHandler persistencyHandler) throws IllegalArgumentException {
		if (activity == null)
			throw new IllegalArgumentException("The activity cannot be null.");
		
		if (paymentEventHandler == null)
			throw new IllegalArgumentException("The payment event handler cannot be null.");
		
		if (userInfos == null)
			throw new IllegalArgumentException("The user infos cannot be null.");
		
		if (serverInfos == null)
			throw new IllegalArgumentException("The server infos cannot be null.");
		
		if (userPrompt == null)
			throw new IllegalArgumentException("The user prompt cannot be null.");
		
		if (persistencyHandler == null)
			throw new IllegalArgumentException("The persistency handler cannot be null.");
	}
	
	private INfcEventHandler nfcEventHandler = new INfcEventHandler() {
		
		@Override
		public void handleMessage(NfcEvent event, Object object) {
			if (Config.DEBUG)
				Log.d(TAG, "Received NfcEvent: "+event);
		
			switch (event) {
			case INIT_FAILED:
			case FATAL_ERROR:
				paymentEventHandler.handleMessage(PaymentEvent.ERROR, null, null);
				terminateTimeoutTask();
				reset();
				break;
			case CONNECTION_LOST:
				connected = false;
				terminateTimeoutTask();
				break;
			case INITIALIZED: //do nothing
				connected = true;
				aborted = false;
				
				if (startTimeoutTask) {
					startTimeoutTask = false;
					startTimeoutTask();
				}
				
				paymentEventHandler.handleMessage(PaymentEvent.INITIALIZED, null, null);
				nofMessages = 0;
				break;
			case MESSAGE_RECEIVED: //do nothing, handle in IMessageHandler
				break;
			}
		}
		
	};
	
	public synchronized void reset() {
		if (Config.DEBUG)
			Log.d(TAG, "Resetting states");
		
		nofMessages = 0;
		persistedPaymentRequest = null;
		startTimeoutTask = false;
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
	
	private byte[] getError(PaymentError err) {
		if (Config.DEBUG)
			Log.d(TAG, "Returning error: "+err);
		
		terminateTimeoutTask();
		aborted = true;
		reset();
		
		paymentEventHandler.handleMessage(PaymentEvent.ERROR, err, null);
		return new PaymentMessage().error().payload(new byte[] { err.getCode() }).bytes();
	}
	
	/*
	 * only for test purposes
	 */
	protected MessageHandler getMessageHandler() {
		return messageHandler;
	}
	
	/*
	 * only for test purposes
	 */
	protected INfcEventHandler getNfcEventHandler() {
		return nfcEventHandler;
	}
	
	protected class MessageHandler implements ITransceiveHandler {

		public byte[] handleMessage(byte[] message, final ISendLater sendLater) {
			if (Config.DEBUG)
				Log.d(TAG, "Received PaymentMessage: "+Arrays.toString(message));
			
			if (aborted)
				return getError(PaymentError.UNEXPECTED_ERROR);
			
			nofMessages++;
			PaymentMessage pm = new PaymentMessage().bytes(message);
			if (pm.isError()) {
				try {
					if (Config.DEBUG)
						Log.d(TAG, "Received PaymentMessage ERROR");
					
					PaymentError paymentError = PaymentError.getPaymentError(pm.payload()[0]);
					return getError(paymentError);
				} catch (Exception e) {
					Log.wtf(TAG, e);
					return getError(PaymentError.UNEXPECTED_ERROR);
				}
			}
			
			if (pm.isPayer()) {
				switch (nofMessages) {
				case 1:
					if (Config.DEBUG)
						Log.d(TAG, "Returning username (payee)");
					
					byte[] bytes = userInfos.getUsername().getBytes(Charset.forName("UTF-8"));
					
					startTimeoutTask();
					
					return new PaymentMessage().payee().payload(bytes).bytes();
				case 2:
					terminateTimeoutTask();
					
					try {
						PaymentResponse paymentResponse = DecoderFactory.decode(PaymentResponse.class, pm.payload());
						boolean signatureValid = paymentResponse.verify(serverInfos.getPublicKey());
						if (!signatureValid) {
							Log.e(TAG, "The signature of the server response is not valid! This might be a Man-In-The-Middle attack, where someone manipulated the server response.");
							return getError(PaymentError.UNEXPECTED_ERROR);
						} else {
							persistencyHandler.delete(persistedPaymentRequest);
							reset();
							
							switch (paymentResponse.getStatus()) {
							case FAILURE:
								if (Config.DEBUG)
									Log.d(TAG, "The server refused the payment");
								
								paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.SERVER_REFUSED, null);
								break;
							case SUCCESS:
								if (Config.DEBUG)
									Log.d(TAG, "The payment request was successful");
								
								paymentEventHandler.handleMessage(PaymentEvent.SUCCESS, paymentResponse, null);
								break;
							case DUPLICATE_REQUEST:
								if (Config.DEBUG)
									Log.d(TAG, "This payment request has already been accepted by the server before");
								
								paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.DUPLICATE_REQUEST, null);
								break;
							}
							
							if (Config.DEBUG)
								Log.d(TAG, "Returning ACK");
							
							return new PaymentMessage().payload(ACK).bytes();
						}
					} catch (Exception e) {
						Log.wtf(TAG, e);
						return getError(PaymentError.UNEXPECTED_ERROR);
					}
				}
			} else {
				switch (nofMessages) {
				case 1:
					try {
						if (Config.DEBUG)
							Log.d(TAG, "About to return signed payment request (payer)");
						
						final InitMessagePayee initMessage = DecoderFactory.decode(InitMessagePayee.class, pm.payload());
						
						boolean paymentAccepted;
						
						if (persistedPaymentRequest != null
								&& persistedPaymentRequest.getUsername().equals(initMessage.getUsername())
								&& persistedPaymentRequest.getCurrency().getCode() == initMessage.getCurrency().getCode()
								&& persistedPaymentRequest.getAmount() == initMessage.getAmount()) {
							/*
							 * this is a payment resume (the user took his
							 * device away to accept/reject the payment)
							 */
							if (Config.DEBUG)
								Log.d(TAG, "Payment resume after reconnection");
							
							paymentAccepted = userPrompt.isPaymentAccepted();
							if(paymentAccepted) {
								if (Config.DEBUG)
									Log.d(TAG, "Payment request has been accepted");
								
								PaymentRequest pr = new PaymentRequest(userInfos.getPKIAlgorithm(), userInfos.getKeyNumber(), userInfos.getUsername(), initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount(), persistedPaymentRequest.getTimestamp());
								pr.sign(userInfos.getPrivateKey());
								byte[] encoded = pr.encode();
								
								persistencyHandler.add(persistedPaymentRequest);
								
								startTimeoutTask();
								
								if (Config.DEBUG)
									Log.d(TAG, "Returning signed payment request (payer)");
								
								sendLater.sendLater(new PaymentMessage().payload(encoded).bytes());
							} else {
								if (Config.DEBUG)
									Log.d(TAG, "Payment request has been rejected by the payer");
								
								//TODO: solve problem with get error (resetting etc)! handle in case 2
								sendLater.sendLater(getError(PaymentError.PAYER_REFUSED));
							}
						} else {
							if (Config.DEBUG)
								Log.d(TAG, "Handle new payment request (wait for user answer)");
							
							persistedPaymentRequest = persistencyHandler.getPersistedPaymentRequest(initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount());
							if (persistedPaymentRequest == null) {
								if (Config.DEBUG)
									Log.d(TAG, "Creating new payment request");
								
								persistedPaymentRequest = new PersistedPaymentRequest(initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount(), System.currentTimeMillis());
							} else {
								if (Config.DEBUG)
									Log.d(TAG, "Loaded payment request from internal storage (previous payment request did not receive any server response)");
							}
							
							userPrompt.promptUserPaymentRequest(initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount(), new IUserPromptAnswer() {
								
								@Override
								public void acceptPayment() {
									try {
										if (Config.DEBUG)
											Log.d(TAG, "Payer accepted payment request");
										
										//response 1st message
										PaymentRequest pr = new PaymentRequest(userInfos.getPKIAlgorithm(), userInfos.getKeyNumber(), userInfos.getUsername(), initMessage.getUsername(), initMessage.getCurrency(), initMessage.getAmount(), persistedPaymentRequest.getTimestamp());
										pr.sign(userInfos.getPrivateKey());
										byte[] encoded = pr.encode();
										
										if (connected)
											startTimeoutTask();
										else
											startTimeoutTask = true;
											
										persistencyHandler.add(persistedPaymentRequest);
										
										if (Config.DEBUG)
											Log.d(TAG, "Returning signed payment request");
										
										//TODO: solve problem with get error (resetting etc)! handle in case 2
										sendLater.sendLater(new PaymentMessage().payload(encoded).bytes());
									} catch (Exception e) {
										Log.wtf(TAG, e);
										sendLater.sendLater(getError(PaymentError.UNEXPECTED_ERROR));
									}
								}
								
								@Override
								public void rejectPayment() {
									if (Config.DEBUG)
										Log.d(TAG, "Payer rejected payment request");
									
									sendLater.sendLater(getError(PaymentError.PAYER_REFUSED));
								}
								
							});
						}
						
						if (Config.DEBUG)
							Log.d(TAG, "Returning null / start polling");
						
						return null;
					} catch (Exception e) {
						Log.wtf(TAG, e);
						return getError(PaymentError.UNEXPECTED_ERROR);
					}
				case 2:
					if (Config.DEBUG)
						Log.d(TAG, "Received server response");
					
					terminateTimeoutTask();
					
					try {
						PaymentResponse paymentResponse = DecoderFactory.decode(PaymentResponse.class, pm.payload());
						boolean signatureValid = paymentResponse.verify(serverInfos.getPublicKey());
						if (!signatureValid) {
							Log.e(TAG, "The signature of the server response is not valid! This might be a Man-In-The-Middle attack, where someone manipulated the server response.");
							return getError(PaymentError.UNEXPECTED_ERROR);
						} else {
							persistencyHandler.delete(persistedPaymentRequest);
							reset();
							switch (paymentResponse.getStatus()) {
							case FAILURE:
								if (Config.DEBUG)
									Log.d(TAG, "The server refused the payment");
								
								paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.SERVER_REFUSED, null);
								break;
							case SUCCESS:
								if (Config.DEBUG)
									Log.d(TAG, "The payment request was successful");
								
								paymentEventHandler.handleMessage(PaymentEvent.SUCCESS, paymentResponse, null);
								break;
							case DUPLICATE_REQUEST:
								if (Config.DEBUG)
									Log.d(TAG, "This payment request has already been accepted by the server before.");
								
								paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.DUPLICATE_REQUEST, null);
								break;
							}
							
							if (Config.DEBUG)
								Log.d(TAG, "Returning ACK");
							
							return new PaymentMessage().payload(ACK).bytes();
						}
					} catch (Exception e) {
						Log.wtf(TAG, e);
						return getError(PaymentError.UNEXPECTED_ERROR);
					}
				}
			}
			
			if (Config.DEBUG)
				Log.d(TAG, "Generic return block - this should never happen");
			
			return getError(PaymentError.UNEXPECTED_ERROR);
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
				if (latch.await(Config.SERVER_RESPONSE_TIMEOUT, TimeUnit.MILLISECONDS)) {
					//countdown reached 0, we wanted to terminate this thread
				} else {
					//waiting time elapsed
					if (Config.DEBUG)
						Log.d(TAG, "Server response timeout (timeout)");
					
					reset();
					paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.NO_SERVER_RESPONSE, null);
				}
			} catch (InterruptedException e) {
				//the current thread has been interrupted
				long now = System.currentTimeMillis();
				if (now - startTime >= Config.SERVER_RESPONSE_TIMEOUT) {
					if (Config.DEBUG)
						Log.d(TAG, "Server response timeout (interrupted)");
					
					reset();
					paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.NO_SERVER_RESPONSE, null);
				}
			}
		}
	}
	
}
