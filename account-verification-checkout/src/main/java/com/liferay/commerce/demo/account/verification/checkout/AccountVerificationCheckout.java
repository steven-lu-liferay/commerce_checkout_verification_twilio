package com.liferay.commerce.demo.account.verification.checkout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.liferay.commerce.account.model.CommerceAccount;
import com.liferay.commerce.constants.CommerceWebKeys;
import com.liferay.commerce.context.CommerceContext;
import com.liferay.commerce.demo.account.verification.checkout.constants.AccountVerificationCheckoutPortletKeys;
import com.liferay.commerce.model.CommerceAddress;
import com.liferay.commerce.model.CommerceOrder;
import com.liferay.commerce.product.service.CommerceChannelLocalService;
import com.liferay.commerce.service.CommerceAddressLocalService;
import com.liferay.commerce.util.BaseCommerceCheckoutStep;
import com.liferay.commerce.util.CommerceCheckoutStep;
import com.liferay.frontend.taglib.servlet.taglib.util.JSPRenderer;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.ResourceBundleUtil;
import com.liferay.portal.kernel.util.WebKeys;

import com.liferay.commerce.util.CommerceCheckoutStepServicesTracker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Jeffrey Handa
 * @author Steven Lu
 */
@Component(
	immediate = true,
	property = {
			"commerce.checkout.step.name=" + AccountVerificationCheckout.NAME,
			"commerce.checkout.step.order:Integer=55"
	},
	service = CommerceCheckoutStep.class
)
public class AccountVerificationCheckout extends BaseCommerceCheckoutStep {

	private static boolean verboseInfo = false;
	private static boolean debugMode = true;
	private static boolean bypassMode = false;
	private static String bypassCode = "12345";
	
	// todo - check if constants are defined in source
	private static final int BILLING_ADDR_TYPE = 1;
	private static final int SHIPPING_ADDR_TYPE = 3;
	private static final int BOTH_ADDR_TYPE = 2;
	
	public static final String NAME = "account-verification";
	
	private Map<Long, OrderVerificationData> orderVerifyLookup = new HashMap<Long, OrderVerificationData>();
	
	@Override
	public String getName() {
		return AccountVerificationCheckout.NAME;
	}

	@Override
	public String getLabel(Locale locale) {
		ResourceBundle resourceBundle = ResourceBundleUtil.getBundle(
				"content.Language", locale, getClass());

		return LanguageUtil.get(resourceBundle, "account-verification");
	}

	@Override
	public boolean isActive(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws Exception {

		/*
			It would be very common to use the CommerceContext at this point to get current context
			(Channel, Account, etc.) and then determine if the Checkout Step should be used.  For example,
			we could add a Custom Field on the Account Object to determine if Account Verification is required,
			or we could simply check for any previous orders and require verification for accounts that have no '
			Completed' orders.  You might also want to require Account Verifiation in the B2B channel, since they
			are getting wholesale pricing, but you don't need to do that in the B2C channel.
		 */

		//return isVerifiedOrder(httpServletRequest);
		
		/*
			It might also be common to put a check here that checks for the items in the cart to determine if a
			checkout step is required or not.  For example, if the product is in the hazardous materials category, then
			we need to ensure customer is qualified to receive those materials.
		 */

		/* 
		 * Current implementation is to show this step always.  If verified, show verified message to user 
		 */
		return true;
	}

	@Override
	public boolean isVisible(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws Exception {

		/*
			This flag determines if the label shows up in the 'timeline' across the top of the checkout widget.
			Even if this is set to false, the Render method will still get called and while on this checkout step
			the label displays.  After completing the step, if the isVisible is still set to false, the label
			disappears from the timeline again.
		 */
		
		return true;
	}

	@Override
	public boolean showControls(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse) {
		
		try {
			boolean verifiedStatus = isVerifiedOrder(httpServletRequest);
			if(verboseInfo) debug("showControls order.verified", verifiedStatus);
			return verifiedStatus;
		}
		catch(PortalException pe) {
			// something went wrong - need to debug
			pe.printStackTrace();
		}
		
		return true;
	}

	@Override
	public void processAction(ActionRequest actionRequest, ActionResponse actionResponse) throws Exception {

		/*
			Just like in a Portlet, this is your opportunity to process the data submitted in the form before
			proceeding to the next step.  If we are storing the Verification status of the Account as a custom
			field or using and Account Group, this would be the place to call the Account service or Account Group
			service and make those updates.
		 */
		
		CommerceContext commerceContext = (CommerceContext) actionRequest.getAttribute(CommerceWebKeys.COMMERCE_CONTEXT);
		CommerceAccount commerceAccount = commerceContext.getCommerceAccount();
		CommerceOrder commerceOrder = commerceContext.getCommerceOrder();
		
		/*
		 *  already verified, nothing more needs to be done ... we should not get this if this step 
		 *  is invisible on a verified order
		 */
		if(isVerifiedOrder(commerceOrder)) {
			if(verboseInfo) debug("processing action on a verified order", commerceOrder.getCommerceOrderId());
			return;
		}
		
		
		OrderVerificationData orderVerifyData = lookupOrderVerification(commerceOrder.getCommerceOrderId());
		
		/*
		 * Process "RESEND" action
		 */
		String actionType = actionRequest.getParameter("actionType");

		String currentUrl = actionRequest.getParameter("redirect");

		if(actionType.equalsIgnoreCase("RESEND")) {
			debug("resending code");
			if(orderVerifyData == null) {
				// something went wrong
				error("!!check why order verify data is null on a resend request");
				return;
			}

			orderVerifyData.clearVerification();
			
			actionRequest.removeAttribute(WebKeys.REDIRECT);
			actionRequest.setAttribute(WebKeys.REDIRECT, currentUrl);
			
			sendVerifyCode(orderVerifyData);
			return;
		}
		

		/*
		 * Process Guest Phone Number
		 */
		if(actionType.equalsIgnoreCase("GUEST-PHONE")) {
			String guestPhoneNumber = getGuestPhoneNumber(actionRequest);
			debug("processing guest phone from param", guestPhoneNumber);
			
			if(guestPhoneNumber != null) {
				orderVerifyData.setVerificationPhone(guestPhoneNumber);
			}
			
			actionRequest.removeAttribute(WebKeys.REDIRECT);
			actionRequest.setAttribute(WebKeys.REDIRECT, currentUrl);

			return;
		}

		/*
		 * Process "VERIFY" action
		 */
		if(actionType.equalsIgnoreCase("VERIFY")) {
			String verifyCode = actionRequest.getParameter("verificationCode").toString();

			debug("check.verify phone, code", new Object[] {orderVerifyData.getVerificationPhone(), verifyCode});
			
			if(checkVerification(verifyCode, orderVerifyData)) {
				orderVerifyData.orderVerified(new Date());
				debug("verification.approved");
			}
			else {
				debug("verification.rejected");
				//orderVerifyData.clearVerification();
				orderVerifyData.verificationFailed();
				actionRequest.setAttribute(WebKeys.REDIRECT, currentUrl);
			}
			
			return;
		}
		
		error("unknown action type", actionType);
	}

	@Override
	public void render(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws Exception {

		/*
			This is a good place to create a 'displayContext' object or helper object that will help your
			checkout step's view.  From the httpServletRequest you can get the CommerceContext to get the Account
			and Channel or get the Order to get the Order Line Items.

			Once you've created your context, or collected any other useful data, you can set them as Attributes
			on the httpServletRequest.
		 */

		CommerceContext commerceContext = (CommerceContext) httpServletRequest.getAttribute(CommerceWebKeys.COMMERCE_CONTEXT);
		CommerceAccount commerceAccount = commerceContext.getCommerceAccount();
		CommerceOrder myOrder = commerceContext.getCommerceOrder();

		String prevStepUrl = getPreviousStepUrl(httpServletRequest, httpServletResponse);
		if(verboseInfo) debug("prev.step.url", prevStepUrl);
		
		httpServletRequest.setAttribute(
				AccountVerificationCheckoutPortletKeys.ACCT_VERIFICATION_PREV_STEP_URL, prevStepUrl);
		
		// should never be null
		if(myOrder == null) {
			error("render order is null");
			
			_jspRenderer.renderJSP(
					_servletContext, httpServletRequest, httpServletResponse,
					"/account_verification_form.jsp");

			return;
		}

		long orderId = myOrder.getCommerceOrderId();
		boolean guestOrderFlag = myOrder.isGuestOrder();
		if(verboseInfo) debug("checkout-render order.id, guest.order", new Object[] {orderId, guestOrderFlag});

		OrderVerificationData verifyData = lookupOrderVerification(orderId);
		
		/*
		 *  Case 1: Navigated from previous checkout step
		 *  There are 2 possible order types:
		 *  (1) Guest Order
		 *  (2) Signed-In Account Order
		 */
		if(verifyData == null) {
			verifyData = new OrderVerificationData(orderId);
			
			if(addOrderVerificationData(verifyData) != null) {
				debug("already existing verification data with order ID", myOrder.getCommerceOrderId());
			}
			
			verifyData.setGuestOrder(guestOrderFlag);
		}
		
		
		httpServletRequest.setAttribute(
				AccountVerificationCheckoutPortletKeys.ACCT_VERIFICATION_DATA_ATTR_KEY, verifyData);
		
		if(verifyData.isGuestOrder() != guestOrderFlag) {
			verifyData.setGuestOrder(guestOrderFlag);
		}
		
		// Case 2: Render phase after resend action processed
		
		// Case 3: Render phase after failed verify attempt

		// Case 4: Render phase after guest phone value action processed
		
		ThemeDisplay themeDisplay = 
				(ThemeDisplay) httpServletRequest.getAttribute(WebKeys.THEME_DISPLAY);
		
		long companyId = themeDisplay.getCompanyId();

		String verificationSid = null;
		
		if(!verifyData.isVerified()) {
			debug("checkout-verification::render - not verified order.");
			if(guestOrderFlag == false) {
				String accountPhoneNumber = getAccountPhoneNumber(commerceAccount, companyId);
				if(accountPhoneNumber != null) {
					debug("set.phone.number [oldphone, newphone]", new Object[] {verifyData.getVerificationPhone(), accountPhoneNumber});
					verifyData.setVerificationPhone(accountPhoneNumber);
				}
				else {
					debug("account phone number is null for non-guest checkout", commerceAccount.getName());
				}
			}
			else {
				String guestBillingPhoneNumber = getGuestPhoneNumber(myOrder);
				
				if(guestBillingPhoneNumber == null) {
					debug("guest.phone.missing");
				}
				else {					
					verifyData.setVerificationPhone(guestBillingPhoneNumber);
				}
			}

			if(verifyData.getVerificationPhone() != null && !verifyData.verificationPending()) {
				debug("checkout-verification::render - sending verification call", verifyData.getVerificationPhone());
				verificationSid = sendVerifyCode(verifyData);
				debug("verification.sids [old, new]", new Object[] {verifyData.getVerifySid(), verificationSid});
				verifyData.setVerifySid(verificationSid);
			}
			else {
				debug("do not send verification code [phone, pending.status]", new Object[] {verifyData.getVerificationPhone(), verifyData.verificationPending()});
			}

		}				
		else {
			debug("checkout-verification::render - already verified.");
		}
		
		_jspRenderer.renderJSP(
				_servletContext, httpServletRequest, httpServletResponse,
				"/account_verification_form.jsp");
		
	}
	
	private String sendVerifyCode(OrderVerificationData verificationData) {
		String toPhone = verificationData.getVerificationPhone();

		if(AccountVerificationCheckout.bypassMode) {
			return bypassSendVerifyCode(toPhone);
		}
		
		String twilioServiceSid = PortalUtil.getPortalProperties().getProperty(AccountVerificationCheckoutPortletKeys.TWILIO_SERVICE_SID_PROP_KEY);
		
		String twilioBasicAuth = getBase64BasicAuthString(); 

		OutputStream os = null;
		InputStream responseStream = null;
		
		StringBuffer responseBuf = new StringBuffer();
		
		try {
			// Create URL to the ReST endpoint
			URL url = new URL("https://verify.twilio.com/v2/Services/" + twilioServiceSid + "/Verifications");

			// Open a connection to the URL
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setDoOutput(true);

			// Now it's "open", we can set the request method, headers etc.
			connection.setRequestMethod("POST");
			connection.setRequestProperty("accept", "application/json");
			connection.setRequestProperty("Authorization", "Basic " + twilioBasicAuth);
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			
			// set the POST data
			String urlParameters  = "To=%2B" + toPhone + "&Channel=sms";
			//urlParameters = java.net.URLEncoder.encode(urlParameters, "UTF-8");
			byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);
			connection.setRequestProperty("Content-Length", Integer.toString(postData.length));
			os = connection.getOutputStream();
			//OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
			os.write(postData);
			os.flush();
			os.close();
			
			connection.connect();		

			// This line makes the request
			responseStream = connection.getInputStream();

			// read the response
			InputStreamReader isr = new InputStreamReader(responseStream);
			
			BufferedReader br = new BufferedReader(isr);
			
			String line = br.readLine();
			while(line != null) {
				responseBuf.append(line);
				line = br.readLine();
			}
			
			br.close();
			isr.close();
			responseStream.close();
			if(verboseInfo) debug(responseBuf);
		}
		catch(IOException ioe) {
			ioe.printStackTrace();
			return null;
		}
		finally {
			if(os != null) {
				try {
					os.close();
				}
				catch(IOException ioe1) {
					ioe1.printStackTrace();
				}
			}
			
			if(responseStream != null) {
				try {
					responseStream.close();
				}
				catch(IOException ioe2) {
					ioe2.printStackTrace();
				}
			}
		}
		if(verboseInfo) debug(responseBuf);

		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		JsonObject responseJson = gson.fromJson(responseBuf.toString(), JsonObject.class);
		JsonArray codeList = responseJson.getAsJsonArray("send_code_attempts");
		String verifySid = codeList.get(0).getAsJsonObject().get("attempt_sid").toString();		
				
		debug("new.verify.sid", verifySid);
		return verifySid;

	}

	private boolean checkVerification(String verifyCode, OrderVerificationData verificationData) {
		String phoneNumber = verificationData.getVerificationPhone();
		
		if(AccountVerificationCheckout.bypassMode) {
			return bypassVerificationCheck(verifyCode, phoneNumber);
		}
		
		String twilioServiceSid = PortalUtil.getPortalProperties().getProperty(AccountVerificationCheckoutPortletKeys.TWILIO_SERVICE_SID_PROP_KEY);

		String twilioBasicAuth = getBase64BasicAuthString(); 

		OutputStream os = null;
		InputStream responseStream = null;
		StringBuffer responseBuf = new StringBuffer();
		
		try {
			// create URL for the ReST endpoint
			URL url = new URL("https://verify.twilio.com/v2/Services/" + twilioServiceSid + "/VerificationCheck");

			// Open a connection on the URL
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setDoOutput(true);

			// Now it's "open", we can set the request method, headers etc.
			connection.setRequestMethod("POST");
			connection.setRequestProperty("accept", "application/json");
			connection.setRequestProperty("Authorization", "Basic " + twilioBasicAuth);
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			
			// set the POST data
			String urlParameters  = "To=%2B" + phoneNumber + "&Channel=sms&Code=" + verifyCode;
			byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);
			connection.setRequestProperty("Content-Length", Integer.toString(postData.length));
			os = connection.getOutputStream();
			os.write(postData);
			os.flush();
			os.close();
			
			connection.connect();		

			// This line makes the request
			responseStream = connection.getInputStream();

			// read the response
			InputStreamReader isr = new InputStreamReader(responseStream);
			
			BufferedReader br = new BufferedReader(isr);
			String line = br.readLine();
			while(line != null) {
				responseBuf.append(line);
				line = br.readLine();
			}
			
			br.close();
			isr.close();
			responseStream.close();
		}
		catch(IOException ioe) {
			ioe.printStackTrace();
			return false;
		}
		finally {
			if(os != null) {
				try {
					os.close();
				}
				catch(IOException ioe1) {
					ioe1.printStackTrace();
				}
			}
			
			if(responseStream != null) {
				try {
					responseStream.close();
				}
				catch(IOException ioe2) {
					ioe2.printStackTrace();
				}
			}
		}
		
		if(verboseInfo) debug(responseBuf);
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		JsonObject responseJson = gson.fromJson(responseBuf.toString(), JsonObject.class);
		String verifyStatus = responseJson.get("status").getAsString();

        return verifyStatus.equalsIgnoreCase("approved");
	}
	
	private boolean isVerifiedOrder(HttpServletRequest httpServletRequest) 
			throws PortalException {

		CommerceContext commerceContext = (CommerceContext) httpServletRequest.getAttribute(CommerceWebKeys.COMMERCE_CONTEXT);
		CommerceOrder commerceOrder = commerceContext.getCommerceOrder();
		
		return isVerifiedOrder(commerceOrder);
	}
	
	private boolean isVerifiedOrder(CommerceOrder order) {
		long orderId = order.getCommerceOrderId();
		OrderVerificationData verifyData = lookupOrderVerification(orderId);
		
		return verifyData != null && verifyData.isVerified();
	}
	
	private OrderVerificationData lookupOrderVerification(long orderId) {
		// can add additional logic if needed
		return orderVerifyLookup.get(orderId);
	}
	
	/**
	 * @return previous verification data
	 */
	private OrderVerificationData addOrderVerificationData(OrderVerificationData verifyData) {
		return orderVerifyLookup.put(verifyData.getOrderId(), verifyData);
	}

	private String getGuestPhoneNumber(ActionRequest actionRequest) {
		String guestPhoneParam = actionRequest.getParameter("guest-phone");
		
		return getGuestPhoneNumber(guestPhoneParam);			
	}
	
	private String getGuestPhoneNumber(CommerceOrder commerceOrder) {
		try {
			CommerceAddress billingAddress = commerceOrder.getBillingAddress();
			
			String guestBillingPhoneNumber = billingAddress.getPhoneNumber();
			
			return getGuestPhoneNumber(guestBillingPhoneNumber);
			
		}
		catch(PortalException pe) {
			pe.printStackTrace();
		}
		
		return null;
	}
	
	private String getGuestPhoneNumber(String guestPhoneValue) {
		if(guestPhoneValue == null) {
			return null;
		}

		guestPhoneValue = cleanPhoneNumber(guestPhoneValue);
		
		return guestPhoneValue;
	}

	/**
	 * @return default billing phone if possible, null if no billing phone specified
	 */
	private String getAccountPhoneNumber(CommerceAccount commerceAcct, long companyId) {
		// Try to get the default billing phone number first
		long defaultBillingAddrId = commerceAcct.getDefaultBillingAddressId();
		try {
			CommerceAddress defaultBillingAddr = 
					_commerceAddressLocalService.getCommerceAddress(defaultBillingAddrId);
			
			if(defaultBillingAddr != null) {
				String defaultBillingPhone = defaultBillingAddr.getPhoneNumber();
				if(defaultBillingPhone != null) {
					return cleanPhoneNumber(defaultBillingPhone);
				}
				else {
					debug("default.billing.phone is null");
				}
			}
		}
		catch(PortalException pe) {
			debug("default.billing.address", pe.getMessage());
		}
	
		// No default billing phone number found, iterate through all billing addresses and use the first number
		List<CommerceAddress> commerceAddrList = 
				_commerceAddressLocalService.getCommerceAddressesByCompanyId(
						companyId, 
						CommerceAccount.class.getName(), 
						commerceAcct.getCommerceAccountId());
		
		if(commerceAddrList == null) {
			debug("commerce.addr.list is null");
			return null;
		}
		
		for(CommerceAddress commerceAddr : commerceAddrList) {
			// verification must go to billing address
			if(commerceAddr.getType() == BILLING_ADDR_TYPE || 
			   commerceAddr.getType() == BOTH_ADDR_TYPE) {
				
				String commercePhone = commerceAddr.getPhoneNumber();
				if(verboseInfo) debug("processing billing phone", commercePhone);
				if(commercePhone != null) {
					return cleanPhoneNumber(commercePhone);
				}
			}
		}
		
		return null;		
	}
	
	private static String cleanPhoneNumber(String phoneNumber) {
		// remove non-numeric characters
		StringBuffer cleanedPhoneBuffer = new StringBuffer();
		for(int i = 0; i < phoneNumber.length(); i++) {
			if(Character.isDigit(phoneNumber.charAt(i))) {
				cleanedPhoneBuffer.append(phoneNumber.charAt(i));
			}
		}
		
		return cleanedPhoneBuffer.toString();
	}
	
	private static String getBase64BasicAuthString() {
		String twilioAcctId = PortalUtil.getPortalProperties().getProperty(AccountVerificationCheckoutPortletKeys.TWILIO_ACCT_ID_PROP_KEY);
		String twilioAuthToken = PortalUtil.getPortalProperties().getProperty(AccountVerificationCheckoutPortletKeys.TWILIO_AUTH_TOKEN_PROP_KEY);

		String authStr = twilioAcctId + ":" + twilioAuthToken;
		return Base64.getEncoder().encodeToString(authStr.getBytes()); 
	}

	private static boolean bypassVerificationCheck(String code, String phoneNumber) {
		return code.equals(AccountVerificationCheckout.bypassCode);
	}
	
	private static String bypassSendVerifyCode(String phoneNumber) {
		return AccountVerificationCheckout.bypassCode;
	}
	
	private String getPreviousStepUrl(HttpServletRequest request, HttpServletResponse response) {
		try {
			String currentUrl = (String) request.getAttribute("CURRENT_COMPLETE_URL");

			CommerceCheckoutStep prevStep = _commerceCheckoutStepServicesTracker.getPreviousCommerceCheckoutStep(
					NAME, request, response);
			
			return currentUrl.replace(NAME, prevStep.getName());
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}

	private static void debug(StringBuffer msgBuffer) {
		if(AccountVerificationCheckout.debugMode) {
			System.out.println(msgBuffer);
		}
	}

	private static void debug(String msg) {
		if(AccountVerificationCheckout.debugMode) {
			System.out.println(msg);
		}
	}

	private static void debug(String msg, Object value) {
		if(AccountVerificationCheckout.debugMode) {
			System.out.println(msg + ": " + value);
		}
	}
	
	private static void debug(String msg, Object[] valueList) {
		if(AccountVerificationCheckout.debugMode) {
			System.out.print(msg + ":[");
			for(int i = 0; i < valueList.length; i++) {
				System.out.print(valueList[i]);
				if(i < valueList.length-1) {
					System.out.print(", ");
				}
			}
			System.out.println("]");
		}
	}
	
	private static void error(String msg) {
		System.err.println("Error!! Message: " + msg);
	}
	
	private static void error(String msg, Object value) {
		System.err.println("Error!! Message: " + msg + ": " + value);
	}
	

	@Reference
	private JSPRenderer _jspRenderer;

	@Reference(
		target = "(osgi.web.symbolicname=com.liferay.commerce.demo.account.verification.checkout)"
	)
	private ServletContext _servletContext;

	private static final Log _log = LogFactoryUtil.getLog(
			AccountVerificationCheckout.class);

	@Reference
	private CommerceChannelLocalService _commerceChannelLocalService;
	
	@Reference
	private CommerceAddressLocalService _commerceAddressLocalService;

	@Reference
	private CommerceCheckoutStepServicesTracker _commerceCheckoutStepServicesTracker;
 
}