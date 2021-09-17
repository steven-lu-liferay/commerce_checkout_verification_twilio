package com.liferay.commerce.demo.account.verification.checkout;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class OrderVerificationData {
	
	/*
	 * Does Twilio handle failed attempts and time outs?
	 */
	private static final int DEFAULT_VALID_VERIFY_DURATION_MINUTES = 5;
	private static final int DEFAULT_MAX_FAILED_ATTEMPTS = 3;

	private int failedAttempts = 0;
	private int maxFailedAttempts = DEFAULT_MAX_FAILED_ATTEMPTS;
	private int minutesVerificationValid = -1;
	
	private long orderId;
	private String verificationPhone = null;
	private boolean guestOrderFlag = false;
	private boolean verifiedFlag = false;
	private Date verifiedTime = null;
	
	private String verifySid = null;
	
	public OrderVerificationData(long orderId) {
		this(orderId, 
			 DEFAULT_VALID_VERIFY_DURATION_MINUTES,
			 DEFAULT_MAX_FAILED_ATTEMPTS);
	}

	public OrderVerificationData(long orderId, int minutesVerificationValid, int maxFailedAttempts) {
		setOrderId(orderId);
		setMinutesVerificationValid(minutesVerificationValid);
		setMaxFailedAttempts(maxFailedAttempts);
	}
	
	public void setOrderId(long orderId) {
		this.orderId = orderId;
	}
	
	public long getOrderId() {
		return orderId;
	}
	
	public String getVerificationPhone() {
		return verificationPhone;
	}
	
	public void setVerificationPhone(String phoneNumber) {
		boolean phoneNumberChanged = false;
		if(phoneNumber == null || verificationPhone == null) {
			// one of the phone numbers is null
			phoneNumberChanged = (phoneNumber != verificationPhone);
		}
		else {
			// both not null
			phoneNumberChanged = !verificationPhone.equals(phoneNumber);
		}
		
		if(phoneNumberChanged) {
			verificationPhone = phoneNumber;
			clearVerification();
		}
	}
	
	public void setGuestOrder(boolean guestOrderFlag) {
		this.guestOrderFlag = guestOrderFlag;
	}
	
	public boolean isGuestOrder() {
		return guestOrderFlag;
	}
	
	public String getVerifySid() {
		return verifySid;
	}
	
	/*
	 * This method is invoked on a RESEND or first time code is sent
	 */
	public void setVerifySid(String verifySid) {
		// Sid from null to non-null or Sid being set to null.  Either case, verification is no longer valid
		if(this.verifySid == null || verifySid == null) {
			clearVerification();
			this.verifySid = verifySid;
			return;
		}
		
		// both not null, check if there is a change
		if(!this.verifySid.equalsIgnoreCase(verifySid)) {
			clearVerification();
			this.verifySid = verifySid;
		}
	}
	
	public void orderVerified(Date verifiedTime) {
		this.verifiedFlag = true;
		this.verifiedTime = verifiedTime;
		this.failedAttempts = 0;
	}
	
	/**
	 * @return if reached max failed attempts
	 */
	public boolean verificationFailed() {
		verifiedFlag = false;
		verifiedTime = null;

		failedAttempts++;
		
		return failedAttempts >= maxFailedAttempts;
	}
	
	public boolean verificationPending() {
		if(isVerified()) {
			return false;
		}
		
		// if was not verified due to expired Verification Sid, verification would have been cleared.
		// check if Verification Sid exists
		return verifySid != null;
	}
	
	public void clearVerification() {
		this.verifiedFlag = false;
		this.verifiedTime = null;
		this.verifySid = null;
		this.failedAttempts = 0;
	}
	
	public boolean isVerified() {
		// not verified since verified time doesn't exist
		if(verifiedTime == null) {
			return false;
		}

		// if expired, set this order to unverified
		if(ChronoUnit.MINUTES.between(verifiedTime.toInstant(), Instant.now()) 
				>= minutesVerificationValid) {

			System.out.println("verification expired: " + verifiedTime);
			clearVerification();
		}
		
		return verifiedFlag;
	}
	
	private void setMinutesVerificationValid(int minutesVerificationValid) {
		this.minutesVerificationValid = minutesVerificationValid;
	}
	
	private void setMaxFailedAttempts(int maxFailedAttempts) {
		this.maxFailedAttempts = maxFailedAttempts;
	}
}
