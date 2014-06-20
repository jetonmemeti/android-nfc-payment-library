package ch.uzh.csg.paymentlib;

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
import ch.uzh.csg.nfclib.NfcEventInterface;
import ch.uzh.csg.nfclib.transceiver.ExternalNfcTransceiver;
import ch.uzh.csg.nfclib.transceiver.InternalNfcTransceiver;
import ch.uzh.csg.nfclib.transceiver.NfcLibException;
import ch.uzh.csg.nfclib.transceiver.NfcTransceiver;
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
		NfcEventInterface nfcEventHandler;
		if (this.paymentType == PaymentType.REQUEST_PAYMENT)
			nfcEventHandler = nfcEventHandlerRequest;
		else
			nfcEventHandler = nfcEventHandlerSend;
		
		if (nfcTransceiver != null) {
			this.nfcTransceiver = nfcTransceiver;
		} else {
			if (ExternalNfcTransceiver.isExternalReaderAttached(activity))
				this.nfcTransceiver = new ExternalNfcTransceiver(nfcEventHandler, userInfos.getUserId());
			else
				this.nfcTransceiver = new InternalNfcTransceiver(nfcEventHandler, userInfos.getUserId());
			
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
	protected NfcEventInterface getNfcEventHandlerRequest() {
		return nfcEventHandlerRequest;
	}
	
	private NfcEventInterface nfcEventHandlerRequest = new NfcEventInterface() {
		
		@Override
		public void handleMessage(NfcEvent event, Object object) {
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
			case MESSAGE_RETURNED: // do nothing, concerns only the HCE
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
			PaymentResponse paymentResponse;
			if (serverPaymentResponse.getPaymentResponsePayee() != null)
				paymentResponse = serverPaymentResponse.getPaymentResponsePayee();
			else
				paymentResponse = serverPaymentResponse.getPaymentResponsePayer();
			
			boolean signatureValid = paymentResponse.verify(serverInfos.getPublicKey());
			if (!signatureValid) {
				Log.d(TAG, "signature not valid");
				sendError(PaymentError.UNEXPECTED_ERROR);
			} else {
				switch (paymentResponse.getStatus()) {
				case FAILURE:
					Log.d(TAG, "payment failure");
					paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.SERVER_REFUSED);
					break;
				case SUCCESS:
					Log.d(TAG, "payment success");
					paymentEventHandler.handleMessage(PaymentEvent.SUCCESS, paymentResponse);
					break;
				case DUPLICATE_REQUEST:
					Log.d(TAG, "payment duplicate");
					paymentEventHandler.handleMessage(PaymentEvent.ERROR, PaymentError.DUPLICATE_REQUEST);
					break;
				}
				
				byte[] encode = serverPaymentResponse.getPaymentResponsePayer().encode();
				
				Log.d(TAG, "DBG2: "+Arrays.toString(encode)+ "//"+serverInfos.getPublicKey());
				nfcTransceiver.transceive(new PaymentMessage().payee().payload(encode).bytes());
			}
		} catch (Exception e) {
			Log.e(TAG, "exception", e);
			sendError(PaymentError.UNEXPECTED_ERROR);
		}
	}

	private NfcEventInterface nfcEventHandlerSend = new NfcEventInterface() {
		
		@Override
		public void handleMessage(NfcEvent event, Object object) {
			// TODO Auto-generated method stub
			switch (event) {
			case FATAL_ERROR:
				break;
			case CONNECTION_LOST:
				break;
			case INITIALIZED:
				break;
			case INIT_FAILED:
				break;
			case MESSAGE_RECEIVED:
				break;
			case MESSAGE_RETURNED:
				break;
			case MESSAGE_SENT:
				break;
			default:
				break;
			}
		}
	};

}
