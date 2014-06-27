package ch.uzh.csg.paymentlib;

import java.nio.charset.Charset;
import java.util.Arrays;

import android.app.Activity;
import android.util.Log;
import ch.uzh.csg.mbps.customserialization.DecoderFactory;
import ch.uzh.csg.mbps.customserialization.InitMessagePayee;
import ch.uzh.csg.mbps.customserialization.PaymentRequest;
import ch.uzh.csg.mbps.customserialization.PaymentResponse;
import ch.uzh.csg.mbps.customserialization.ServerPaymentRequest;
import ch.uzh.csg.mbps.customserialization.ServerPaymentResponse;
import ch.uzh.csg.nfclib.NfcEvent;
import ch.uzh.csg.nfclib.NfcLibException;
import ch.uzh.csg.nfclib.NfcTransceiver;
import ch.uzh.csg.paymentlib.container.PaymentInfos;
import ch.uzh.csg.paymentlib.container.ServerInfos;
import ch.uzh.csg.paymentlib.container.UserInfos;
import ch.uzh.csg.paymentlib.exceptions.IllegalArgumentException;
import ch.uzh.csg.paymentlib.exceptions.UnknownPaymentErrorException;
import ch.uzh.csg.paymentlib.messages.PaymentError;
import ch.uzh.csg.paymentlib.messages.PaymentMessage;
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
	
	//TODO jeton: offer disable() API to disable a view?
	
	public static final String TAG = "##NFC## PaymentRequestInitializer";
	
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
	
	private volatile NfcTransceiver nfcTransceiver;
	private int nofMessages = 0;
	private volatile boolean aborted = false;
	private boolean disabled = false;
	
	private Thread timeoutThread;
	private volatile boolean serverResponseArrived = false;
	
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
	 * @param type
	 *            the {@link PaymentType}
	 * @throws IllegalArgumentException
	 *             if any parameter is not valid (e.g., null)
	 * @throws NfcLibException
	 *             if the underlying NFC feature cannot be used for any reason
	 */
	public PaymentRequestInitializer(Activity activity, IPaymentEventHandler paymentEventHandler, UserInfos userInfos, PaymentInfos paymentInfos, ServerInfos serverInfos, PaymentType type) throws IllegalArgumentException,  NfcLibException {
		this(activity, null, paymentEventHandler, userInfos, paymentInfos, serverInfos, type);
	}
	
	/*
	 * This constructor is only for test purposes, in order to mock the
	 * NfcTransceiver. For productive use the public constructor, otherwise the
	 * NFC will not work.
	 */
	protected PaymentRequestInitializer(Activity activity, NfcTransceiver nfcTransceiver, IPaymentEventHandler paymentEventHandler, UserInfos userInfos, PaymentInfos paymentInfos, ServerInfos serverInfos, PaymentType type) throws IllegalArgumentException, NfcLibException {
		checkParameters(activity, paymentEventHandler, userInfos, paymentInfos, serverInfos, type);
		
		this.paymentType = type;
		this.activity = activity;
		this.paymentEventHandler = paymentEventHandler;
		this.userInfos = userInfos;
		this.serverInfos = serverInfos;
		this.paymentInfos = paymentInfos;
		
		initPayment(nfcTransceiver);
	}

	private void checkParameters(Activity activity, IPaymentEventHandler paymentEventHandler, UserInfos userInfos, PaymentInfos paymentInfos, ServerInfos serverInfos, PaymentType type) throws IllegalArgumentException {
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
	}
	
	private void initPayment(NfcTransceiver nfcTransceiver) throws NfcLibException {
		NfcEvent nfcEventHandler;
		if (this.paymentType == PaymentType.REQUEST_PAYMENT)
			nfcEventHandler = nfcEventHandlerRequest;
		else
			nfcEventHandler = nfcEventHandlerSend;
		
		if (nfcTransceiver != null) {
			this.nfcTransceiver = nfcTransceiver;
		} else {
			this.nfcTransceiver = new NfcTransceiver(nfcEventHandler, activity, userInfos.getUserId());
			
			Log.d(TAG, "init and enable transceiver");
			this.nfcTransceiver.enable(activity);
		}
	}
	
	private void sendError(PaymentError err) {
		aborted = true;
		nfcTransceiver.transceive(new PaymentMessage().error().payload(new byte[] { err.getCode() }).bytes());
		paymentEventHandler.handleMessage(PaymentEvent.ERROR, err);
	}
	
	/*
	 * only for test purposes
	 */
	protected NfcEvent getNfcEventHandler() {
		if (paymentType == PaymentType.REQUEST_PAYMENT)
			return nfcEventHandlerRequest;
		else if (paymentType == PaymentType.SEND_PAYMENT)
			return nfcEventHandlerSend;
		else
			return null;
	}
	
	private NfcEvent nfcEventHandlerRequest = new NfcEvent() {
		
		@Override
		public void handleMessage(Type event, Object object) {
			if(object instanceof byte[]) {
				Log.d(TAG, "handle payment request init message: "+event.name()+ " / " + Arrays.toString((byte[]) object));
			} else {
				Log.d(TAG, "handle payment request init message: "+event.name()+ " / " + object);
			}
			if (aborted) {
				if (!disabled) {
					nfcTransceiver.disable(activity);
					disabled = true;
				}
				return;
			}
			
			switch (event) {
			case INIT_FAILED:
			case FATAL_ERROR:
				aborted = true;
				paymentEventHandler.handleMessage(PaymentEvent.ERROR, null);
				nfcTransceiver.disable(activity);
				break;
			case CONNECTION_LOST:
				//TODO jeton: abort timeout thread here!
				if (timeoutThread != null && timeoutThread.isAlive())
					timeoutThread.interrupt();
				
				nofMessages = 0;
				break;
			case INITIALIZED:
				try {
					InitMessagePayee initMessage = new InitMessagePayee(userInfos.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount());
					nfcTransceiver.transceive(new PaymentMessage().payee().payload(initMessage.encode()).bytes());
				} catch (Exception e) {
					sendError(PaymentError.UNEXPECTED_ERROR);
				}
				break;
			case MESSAGE_RECEIVED:
				nofMessages++;
				if (object == null || !(object instanceof byte[])) {
					sendError(PaymentError.UNEXPECTED_ERROR);
					break;
				}
				byte[] tmp = (byte[]) object;
				PaymentMessage response = new PaymentMessage().bytes(tmp);
				if (response.isError()) {
					PaymentError paymentError = null;
					if (response.payload() != null && response.payload().length > 0) {
						try {
							paymentError = PaymentError.getPaymentError(response.payload()[0]);
						} catch (UnknownPaymentErrorException e) {
						}
					}
					
					paymentEventHandler.handleMessage(PaymentEvent.ERROR, paymentError);
					nfcTransceiver.disable(activity);
					break;
				}
				
				switch (nofMessages) {
				case 1:
					try {
						PaymentRequest paymentRequestPayer = DecoderFactory.decode(PaymentRequest.class, response.payload());
						PaymentRequest paymentRequestPayee = new PaymentRequest(userInfos.getPKIAlgorithm(), userInfos.getKeyNumber(), paymentRequestPayer.getUsernamePayer(), userInfos.getUsername(), paymentInfos.getCurrency(), paymentInfos.getAmount(), paymentRequestPayer.getTimestamp());
						if (!paymentRequestPayer.requestsIdentic(paymentRequestPayee)) {
							sendError(PaymentError.REQUESTS_NOT_IDENTIC);
						} else {
							paymentRequestPayee.sign(userInfos.getPrivateKey());
							ServerPaymentRequest spr = new ServerPaymentRequest(paymentRequestPayer, paymentRequestPayee);
							paymentEventHandler.handleMessage(PaymentEvent.FORWARD_TO_SERVER, spr.encode(), PaymentRequestInitializer.this);
							
							if (timeoutThread != null && timeoutThread.isAlive())
								timeoutThread.interrupt();
							
							timeoutThread = new Thread(new ServerTimeoutHandler());
							timeoutThread.start();
						}
					} catch (Exception e) {
						sendError(PaymentError.UNEXPECTED_ERROR);
					}
					break;
				case 2:
					if (timeoutThread != null && timeoutThread.isAlive())
						timeoutThread.interrupt();
					
					//TODO: already fired once on server response
//					paymentEventHandler.handleMessage(PaymentEvent.SUCCESS, object);
//					nfcTransceiver.disable(activity);
					break;
				}
				break;
			}
		}
	};
	
	private class ServerTimeoutHandler implements Runnable {

		public void run() {
			long startTime = System.currentTimeMillis();
			
			while (!serverResponseArrived) {
				long now = System.currentTimeMillis();
				if (now - startTime > Config.SERVER_CALL_TIMEOUT) {
					aborted = true;
					paymentEventHandler.handleMessage(PaymentEvent.NO_SERVER_RESPONSE, null);
					PaymentMessage pm = new PaymentMessage().error().payload(new byte[] { PaymentError.NO_SERVER_RESPONSE.getCode() });
					nfcTransceiver.transceive(pm.bytes());
					break;
				}
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					break;
				}
			}
		}
		
	}
	
	@Override
	public void onServerResponse(ServerPaymentResponse serverPaymentResponse) {
		serverResponseArrived = true;
		
		try {
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
			
			boolean signatureValid = toProcess.verify(serverInfos.getPublicKey());
			if (!signatureValid) {
				Log.d(TAG, "signature not valid");
				sendError(PaymentError.UNEXPECTED_ERROR);
			} else {
				switch (toProcess.getStatus()) {
				case FAILURE:
					Log.d(TAG, "payment failure");
					paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.SERVER_REFUSED);
					break;
				case SUCCESS:
					Log.d(TAG, "payment success");
					paymentEventHandler.handleMessage(PaymentEvent.SUCCESS, toProcess);
					break;
				case DUPLICATE_REQUEST:
					Log.d(TAG, "payment duplicate");
					paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.DUPLICATE_REQUEST);
					break;
				}
				
				byte[] encode = toForward.encode();
				Log.d(TAG, "DBG2: "+Arrays.toString(encode)+ "//"+serverInfos.getPublicKey());
				
				switch (paymentType) {
				case REQUEST_PAYMENT:
					nfcTransceiver.transceive(new PaymentMessage().payee().payload(encode).bytes());
					break;
				case SEND_PAYMENT:
					nfcTransceiver.transceive(new PaymentMessage().payer().payload(encode).bytes());
					break;
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "exception", e);
			sendError(PaymentError.UNEXPECTED_ERROR);
		}
	}

	private NfcEvent nfcEventHandlerSend = new NfcEvent() {
		
		@Override
		public void handleMessage(Type event, Object object) {	
			if(object instanceof byte[]) {
				Log.d(TAG, "handle payment request init message: "+event.name()+ " / " + Arrays.toString((byte[]) object));
			} else {
				Log.d(TAG, "handle payment request init message: "+event.name()+ " / " + object);
			}
			if (aborted) {
				if (!disabled) {
					nfcTransceiver.disable(activity);
					disabled = true;
				}
				return;
			}
			
			switch (event) {
			case INIT_FAILED:
			case FATAL_ERROR:
				aborted = true;
				paymentEventHandler.handleMessage(PaymentEvent.ERROR, null);
				//nfcTransceiver.disable(activity);
				break;
			case CONNECTION_LOST:
				//TODO jeton: abort timeout thread here!
				if (timeoutThread != null && timeoutThread.isAlive())
					timeoutThread.interrupt();
				
				nofMessages = 0;
				break;
			case INITIALIZED:
				try {
					//send empty message, we just need the payee's username
					nfcTransceiver.transceive(new PaymentMessage().payer().payload(new byte[] { 0x00 }).bytes());
				} catch (Exception e) {
					sendError(PaymentError.UNEXPECTED_ERROR);
				}
				break;
			case MESSAGE_RECEIVED:
				nofMessages++;
				if (object == null || !(object instanceof byte[])) {
					sendError(PaymentError.UNEXPECTED_ERROR);
					break;
				}
				byte[] tmp = (byte[]) object;
				PaymentMessage response = new PaymentMessage().bytes(tmp);
				if (response.isError()) {
					PaymentError paymentError = null;
					if (response.payload() != null && response.payload().length > 0) {
						try {
							paymentError = PaymentError.getPaymentError(response.payload()[0]);
						} catch (UnknownPaymentErrorException e) {
						}
					}
					
					paymentEventHandler.handleMessage(PaymentEvent.ERROR, paymentError);
					//nfcTransceiver.disable(activity);
					break;
				}
				
				switch (nofMessages) {
				case 1:
					//TODO: load from/save to persisted payment request! avoid double spending problems!! see PaymentRequestHandler
					String usernamePayee = new String(response.payload(), Charset.forName("UTF-8"));
					try {
						PaymentRequest paymentRequestPayer = new PaymentRequest(userInfos.getPKIAlgorithm(), userInfos.getKeyNumber(), userInfos.getUsername(), usernamePayee, paymentInfos.getCurrency(), paymentInfos.getAmount(), paymentInfos.getTimestamp());
						paymentRequestPayer.sign(userInfos.getPrivateKey());
						ServerPaymentRequest spr = new ServerPaymentRequest(paymentRequestPayer);
						paymentEventHandler.handleMessage(PaymentEvent.FORWARD_TO_SERVER, spr.encode(), PaymentRequestInitializer.this);
						
						if (timeoutThread != null && timeoutThread.isAlive())
							timeoutThread.interrupt();
						
						timeoutThread = new Thread(new ServerTimeoutHandler());
						timeoutThread.start();
					} catch (Exception e) {
						sendError(PaymentError.UNEXPECTED_ERROR);
					}
					break;
				case 2:
					if (timeoutThread != null && timeoutThread.isAlive())
						timeoutThread.interrupt();
					
					//TODO: already fired once on server resposne
//					paymentEventHandler.handleMessage(PaymentEvent.SUCCESS, object);
					//nfcTransceiver.disable(activity);
					break;
				}
				break;
			}
		}
	};

}
