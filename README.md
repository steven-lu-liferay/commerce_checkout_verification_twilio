<h3>DESCRIPTION</h3>
<p>This checkout step in the Commerce checkout process will send an sms message to either an Account billing address phone number or a phone number entered as part of a guest checkout process.</p>
<br>

<h3>INSTRUCTIONS</h3>
<br>
<h5>Required properties for twilio authentication</h5>
twilio.account.id=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
<br>
twilio.auth.token=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

<h5>Service Sid to make the api calls</h5>
twilio.service.sid=VAxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
<br>

<h5>Format of phone number</h5>
<p>Phone numbers must include the country code.  For example an US number 310-123-4567 has to be entered as 1-310-123-4567.</p>
<br>

<h5>Phone number selection sequence for <b>authenticated (non-guest)</b> user Accounts</h5>
<p>The default billing address phone number will be used if one exists.  Otherwise, the first phone number from any billing address for the Account will be used.</p>
