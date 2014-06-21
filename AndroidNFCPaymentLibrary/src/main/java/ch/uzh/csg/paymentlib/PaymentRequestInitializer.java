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

//TODO: javadoc
public class PaymentRequestInitializer implements IServerResponseListener {
	
	public static final String TAG = "##NFC## PaymentRequestInitializer";
	
	/*
	 * seller/payee inits the payment --> type: request_payment
	 * buyer/payer inits the payment --> type : send_payment
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
	
	private Thread timeoutHandler;
	private volatile boolean serverResponseArrived = false;
	
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
	protected NfcEvent getNfcEventHandlerRequest() {
		return nfcEventHandlerRequest;
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
				nofMessages = 0;
				break;
			case MESSAGE_SENT_HCE: // do nothing, concerns only the HCE
				break;
			case MESSAGE_SENT:
				nofMessages++;
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
							
							if (timeoutHandler != null && !timeoutHandler.isInterrupted()) {
								timeoutHandler.interrupt();
							}
							timeoutHandler = new Thread(new ServerTimeoutHandler());
							timeoutHandler.start();
						}
					} catch (Exception e) {
						sendError(PaymentError.UNEXPECTED_ERROR);
					}
					break;
				case 2:
					if (timeoutHandler != null && !timeoutHandler.isInterrupted())
						 timeoutHandler.interrupt();
					
					nfcTransceiver.disable(activity);
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
				nfcTransceiver.disable(activity);
				break;
			case CONNECTION_LOST:
				nofMessages = 0;
				break;
			case MESSAGE_SENT_HCE: // do nothing, concerns only the HCE
				break;
			case MESSAGE_SENT:
				nofMessages++;
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
					String usernamePayee = new String(response.payload(), Charset.forName("UTF-8"));
					try {
						PaymentRequest paymentRequestPayer = new PaymentRequest(userInfos.getPKIAlgorithm(), userInfos.getKeyNumber(), userInfos.getUsername(), usernamePayee, paymentInfos.getCurrency(), paymentInfos.getAmount(), paymentInfos.getTimestamp());
						paymentRequestPayer.sign(userInfos.getPrivateKey());
						ServerPaymentRequest spr = new ServerPaymentRequest(paymentRequestPayer);
						paymentEventHandler.handleMessage(PaymentEvent.FORWARD_TO_SERVER, spr.encode(), PaymentRequestInitializer.this);
						
						if (timeoutHandler != null && !timeoutHandler.isInterrupted()) {
							timeoutHandler.interrupt();
						}
						timeoutHandler = new Thread(new ServerTimeoutHandler());
						timeoutHandler.start();
					} catch (Exception e) {
						sendError(PaymentError.UNEXPECTED_ERROR);
					}
					break;
				case 2:
					if (timeoutHandler != null && !timeoutHandler.isInterrupted())
						timeoutHandler.interrupt();
					
					nfcTransceiver.disable(activity);
					break;
				}
				break;
			}
		}
	};

}
