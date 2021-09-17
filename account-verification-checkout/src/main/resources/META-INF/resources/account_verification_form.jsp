<%@ include file="./init.jsp" %>

<%
	OrderVerificationData verifyData = (OrderVerificationData) request.getAttribute(AccountVerificationCheckoutPortletKeys.ACCT_VERIFICATION_DATA_ATTR_KEY);

	String portletNameSpace = themeDisplay.getPortletDisplay().getNamespace();
	String elName = portletNameSpace + "actionType";
	
	String prevStepUrl = (String) request.getAttribute(AccountVerificationCheckoutPortletKeys.ACCT_VERIFICATION_PREV_STEP_URL);
%>

<style>
	.twilio-cancel-btn {
		color: white!important;
	}
</style>

<script type="text/javascript">
	function setActionType(actionType) {
		var elName = "<%= portletNameSpace + "actionType"%>";
		var el = document.getElementById(elName);
		el.value = actionType;
	}
</script>

<portlet:renderURL var="verifyActionURL">
	<portlet:param name="redirect" value="${currentURL}" />
</portlet:renderURL>

<div style="max-width:500px;margin:auto;">

<c:choose>
	<c:when test="<%= verifyData.isVerified() %>">
		<p class="h5 text-center mt-4">You're already verified! Please proceed.</p>
	</c:when>
	<c:otherwise>	
		<p>Account verification required.</p>
		<aui:form action="${verifyActionURL}" name="verificationForm">
			<aui:fieldset-group markupView="lexicon">
		
				<aui:fieldset>
					<aui:input name="actionType" field="actionType" type="hidden" value="VERIFY" />
					<c:choose>
						<c:when test="<%= verifyData.isGuestOrder() && verifyData.getVerificationPhone() == null %>">
							<p>For Guest Verification, please enter your phone number again.</p>
							<aui:input label="Guest Phone Number" name="guest-phone" value="">
							</aui:input>
						</c:when>
						<c:otherwise>
							<p>Please enter the verification code.</p>
							<aui:input name="verificationCode">
							</aui:input>
						</c:otherwise>
					</c:choose>	
				</aui:fieldset>
			</aui:fieldset-group>
			<aui:button-row>
				<c:choose>
					<c:when test="<%= verifyData.isGuestOrder() && verifyData.getVerificationPhone() == null %>">
						<aui:button value="SUBMIT" cssClass="ml-4 float-right btn btn-primary" onClick="setActionType('GUEST-PHONE')" type="submit" />
					</c:when>
					<c:otherwise>
						<aui:button value="VERIFY" cssClass="ml-4 float-right btn btn-primary" type="submit" />
		 				<aui:button value="RESEND CODE" cssClass="float-right twilio-cancel-btn btn btn-primary" onClick="setActionType('RESEND')" type="submit" /> 
		 				<a class="btn pull-left btn-secondary" href="<%= prevStepUrl %>">GO BACK</a>
					</c:otherwise>
				</c:choose>
				
			</aui:button-row>
			
		</aui:form>
	</c:otherwise>
</c:choose>
</div>	
