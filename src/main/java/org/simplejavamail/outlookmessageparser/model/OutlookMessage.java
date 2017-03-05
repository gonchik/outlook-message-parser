package org.simplejavamail.outlookmessageparser.model;

import org.apache.poi.hmef.CompressedRTF;
import org.apache.poi.hsmf.datatypes.MAPIProperty;
import org.simplejavamail.outlookmessageparser.rtf.RTF2HTMLConverter;
import org.simplejavamail.outlookmessageparser.rtf.SimpleRTF2HTMLConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static java.util.regex.Pattern.compile;

/**
 * Class that represents a .msg file. Some fields from the .msg file are stored in special parameters (e.g., {@link #fromEmail}). Attachments are stored in the
 * property {@link #outlookAttachments}). An attachment may be of the type {@link OutlookMsgAttachment} which represents another attached (encapsulated) .msg
 * object.
 */
public class OutlookMessage {
	private static final Logger LOGGER = LoggerFactory.getLogger(OutlookMessage.class);

	private static final String WINDOWS_CHARSET = "CP1252";

	/**
	 * The message class as defined in the .msg file.
	 */
	private String messageClass = "IPM.Note";
	/**
	 * The message Id.
	 */
	private String messageId;
	/**
	 * The address part of From: mail address.
	 */
	private String fromEmail;
	/**
	 * The name part of the From: mail address
	 */
	private String fromName;
	/**
	 * The address part of To: mail address.
	 */
	private String toEmail;
	/**
	 * The name part of the To: mail address
	 */
	private String toName;
	/**
	 * The address part of Reply-To header
	 */
	private String replyToEmail;
	/**
	 * The name part of Reply-To header
	 */
	private String replyToName;
	/**
	 * The mail's subject.
	 */
	private String subject;
	/**
	 * The normalized body text.
	 */
	private String bodyText;
	/**
	 * The displayed To: field
	 */
	private String displayTo;
	/**
	 * The displayed Cc: field
	 */
	private String displayCc;
	/**
	 * The displayed Bcc: field
	 */
	private String displayBcc;

	/**
	 * The body in RTF format (if available)
	 */
	private String bodyRTF;

	/**
	 * The body in HTML format (if available)
	 */
	private String bodyHTML;

	/**
	 * The body in HTML format (converted from RTF)
	 */
	private String convertedBodyHTML;
	/**
	 * Email headers (if available)
	 */
	private String headers;

	/**
	 * Email Date
	 */
	private Date date;

	/**
	 * Client Submit Time
	 */
	private Date clientSubmitTime;

	private Date creationDate;

	private Date lastModificationDate;
	/**
	 * A list of all outlookAttachments (both {@link OutlookFileAttachment}
	 * and {@link OutlookMsgAttachment}).
	 */
	private final List<OutlookAttachment> outlookAttachments = new ArrayList<>();
	/**
	 * Contains all properties that are not
	 * covered by the special properties.
	 */
	private final Map<Integer, Object> properties = new TreeMap<>();
	/**
	 * A list containing all recipients for this message
	 * (which can be set in the 'to:', 'cc:' and 'bcc:' field, respectively).
	 */
	private final List<OutlookRecipient> recipients = new ArrayList<>();

	private final RTF2HTMLConverter rtf2htmlConverter;

	public OutlookMessage() {
		rtf2htmlConverter = new SimpleRTF2HTMLConverter();
	}

	public OutlookMessage(RTF2HTMLConverter rtf2htmlConverter) {
		this.rtf2htmlConverter = (rtf2htmlConverter != null) ? rtf2htmlConverter : new SimpleRTF2HTMLConverter();
	}

	public void addAttachment(OutlookAttachment outlookAttachment) {
		outlookAttachments.add(outlookAttachment);
	}

	public void addRecipient(OutlookRecipient recipient) {
		recipients.add(recipient);
		if (toEmail == null) {
			setToEmail(recipient.getAddress());
		}
		if (toName == null) {
			setToName(recipient.getName());
		}
	}

	/**
	 * Sets the name/value pair in the {@link #properties} map. Some properties are put into special attributes (e.g., {@link #toEmail} when the property name
	 * is '0076').
	 */
	public void setProperty(OutlookMessageProperty msgProp) {
		String name = msgProp.getClazz();
		Object value = msgProp.getData();

		if ((name == null) || (value == null)) {
			return;
		}

		//Most fields expect a String representation of the value
		String stringValue = convertValueToString(value);

		int mapiClass = -1;
		try {
			mapiClass = Integer.parseInt(name, 16);
		} catch (NumberFormatException e) {
			LOGGER.trace("Unexpected type: {}", name, e);
		}

		switch (mapiClass) {
			case 0x1a: //MESSAGE CLASS
				setMessageClass(stringValue);
				break;
			case 0x1035:
				setMessageId(stringValue);
				break;
			case 0x37: //SUBJECT
			case 0xe1d: //NORMALIZED SUBJECT
				setSubject(stringValue);
				break;
			case 0xc1f: //SENDER EMAIL ADDRESS
			case 0x65: //SENT REPRESENTING EMAIL ADDRESS
			case 0x3ffa: //LAST MODIFIER NAME
			case 0x800d:
			case 0x8008:
				setFromEmail(stringValue);
				break;
			case 0x42: //SENT REPRESENTING NAME
				setFromName(stringValue);
				break;
			case 0x76: //RECEIVED BY EMAIL ADDRESS
				setToEmail(stringValue, true);
				break;
			case 0x8000:
				setToEmail(stringValue);
				break;
			case 0x3001: //DISPLAY NAME
				setToName(stringValue);
				break;
			case 0xe04: //DISPLAY TO
				setDisplayTo(stringValue);
				break;
			case 0xe03: //DISPLAY CC
				setDisplayCc(stringValue);
				break;
			case 0xe02: //DISPLAY BCC
				setDisplayBcc(stringValue);
				break;
			case 0x1013: //HTML
				setBodyHTML(stringValue);
				break;
			case 0x1000: //BODY
				setBodyText(stringValue);
				break;
			case 0x1009: //RTF COMPRESSED
				setBodyRTF(value);
				break;
			case 0x7d: //TRANSPORT MESSAGE HEADERS
				setHeaders(stringValue);
				break;
			case 0x3007: //CREATION TIME
				setCreationDate(stringValue);
				break;
			case 0x3008: //LAST MODIFICATION TIME
				setLastModificationDate(stringValue);
				break;
			case 0x39: //CLIENT SUBMIT TIME
				setClientSubmitTime(stringValue);
				break;
		}

		// save all properties (incl. those identified above)
		properties.put(mapiClass, value);

		checkToRecipient();

		// other possible values (some are duplicates)
		// 0044: recv name
		// 004d: author
		// 0050: reply
		// 005a: sender
		// 0065: sent email
		// 0076: received email
		// 0078: repr. email
		// 0c1a: sender name
		// 0e04: to
		// 0e1d: subject normalized
		// 1046: sender email
		// 3003: email address
		// 1008 rtf sync
	}

	private String convertValueToString(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof String) {
			return ((String) value);
		} else if (value instanceof byte[]) {
			try {
				return new String((byte[]) value, "CP1252");
			} catch (UnsupportedEncodingException e) {
				LOGGER.error("Unsupported encoding!", e);
				return null;
			}
		} else {
			LOGGER.trace("Unexpected body class: {} (expected String or byte[])", value.getClass().getName());
			return value.toString();
		}
	}

	/**
	 * Checks if the correct recipient's addresses are set.
	 */
	private void checkToRecipient() {
		OutlookRecipient toRecipient = getToRecipient();
		if (toRecipient != null) {
			setToEmail(toRecipient.getAddress(), true);
			setToName(toRecipient.getName());
			recipients.remove(toRecipient);
			recipients.add(0, toRecipient);
		}
	}

	/**
	 * @return Only the attachments that are embedded by cid reference.
	 */
	public Map<String, OutlookFileAttachment> fetchCIDMap() {
		final HashMap<String, OutlookFileAttachment> cidMap = new HashMap<>();
		final String html = getConvertedBodyHTML();

		if (html != null && html.length() != 0) {
			for (OutlookAttachment attachment : getOutlookAttachments()) {
				if (attachment instanceof OutlookFileAttachment) {
					OutlookFileAttachment fileAttachment = (OutlookFileAttachment) attachment;
					String cid = fileAttachment.getFilename();
					if (cid != null && cid.length() != 0 && htmlContainsCID(html, cid)) {
						cidMap.put(cid, fileAttachment);
					}
				}
			}
		}
		return cidMap;
	}

	/**
	 * @return Only the downloadable attachments, *not* embedded attachments (as in embedded with cid:attachment, such as images in an email).
	 */
	public List<OutlookAttachment> fetchTrueAttachments() {
		Set<OutlookAttachment> allAttachments = new HashSet<>(getOutlookAttachments());
		allAttachments.removeAll(fetchCIDMap().values());
		return new ArrayList<>(allAttachments);
	}

	private boolean htmlContainsCID(String html, String cidName) {
		return compile("cid:['\"]?" + cidName + "['\"]?").matcher(html).find();
	}

	/**
	 * @param date The date string to be converted (e.g.: 'Mon Jul 23 15:43:12 CEST 2012')
	 * @return A {@link Date} object representing the given date string.
	 */
	private static Date parseDateString(String date) {
		//in order to parse the date we try using the US locale before we 
		//fall back to the default locale.
		List<SimpleDateFormat> sdfList = new ArrayList<>(2);
		sdfList.add(new SimpleDateFormat("EEE MMM d HH:mm:ss z yyyy", Locale.US));
		sdfList.add(new SimpleDateFormat("EEE MMM d HH:mm:ss z yyyy"));

		Date d = null;
		for (SimpleDateFormat sdf : sdfList) {
			try {
				d = sdf.parse(date);
				if (d != null) {
					break;
				}
			} catch (ParseException e) {
				LOGGER.trace("Unexpected date format for date {}", date, e);
			}
		}
		return d;
	}

	/**
	 * Decompresses compressed RTF data.
	 *
	 * @param value Data to be decompressed.
	 * @return A byte array representing the decompressed data.
	 */
	private byte[] decompressRtfBytes(byte[] value) {
		byte[] decompressed = null;
		if (value != null) {
			try {
				CompressedRTF crtf = new CompressedRTF();
				decompressed = crtf.decompress(new ByteArrayInputStream(value));
			} catch (IOException e) {
				LOGGER.error("Could not decompress RTF data", e);
			}
		}
		return decompressed;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("From: ").append(createMailString(fromEmail, fromName)).append("\n");
		sb.append("To: ").append(createMailString(toEmail, toName)).append("\n");
		if (date != null) {
			SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.ENGLISH);
			sb.append("Date: ").append(formatter.format(date)).append("\n");
		}
		if (subject != null)
			sb.append("Subject: ").append(subject).append("\n");
		sb.append("").append(outlookAttachments.size()).append(" outlookAttachments.");
		return sb.toString();
	}

	/**
	 * @return All information of this message object.
	 */
	public String toLongString() {
		StringBuilder sb = new StringBuilder();
		sb.append("From: ").append(createMailString(fromEmail, fromName)).append("\n");
		sb.append("To: ").append(createMailString(toEmail, toName)).append("\n");
		if (date != null) {
			SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.ENGLISH);
			sb.append("Date: ").append(formatter.format(date)).append("\n");
		}
		if (subject != null)
			sb.append("Subject: ").append(subject).append("\n");
		sb.append("\n");
		if (bodyText != null)
			sb.append(bodyText);
		if (outlookAttachments.size() > 0) {
			sb.append("\n");
			sb.append("").append(outlookAttachments.size()).append(" outlookAttachments.\n");
			for (OutlookAttachment att : outlookAttachments) {
				sb.append(att).append("\n");
			}
		}
		return sb.toString();
	}

	/**
	 * Convenience method for creating an email address expression (including the name, the address, or both).
	 *
	 * @param mail The mail address.
	 * @param name The name part of the address.
	 * @return A combination of the name and address.
	 */
	private String createMailString(String mail, String name) {
		if ((mail == null) && (name == null)) {
			return null;
		}
		if (name == null) {
			return mail;
		}
		if (mail == null) {
			return name;
		}
		if (mail.equalsIgnoreCase(name)) {
			return mail;
		}
		return "\"" + name + "\" <" + mail + ">";
	}

	/**
	 * Bean getter for {@link #outlookAttachments}.
	 */
	@SuppressWarnings("ElementOnlyUsedFromTestCode")
	public List<OutlookAttachment> getOutlookAttachments() {
		return outlookAttachments;
	}

	/**
	 * Bean getter for {@link #recipients}.
	 */
	public List<OutlookRecipient> getRecipients() {
		return recipients;
	}

	/**
	 * Bean getter for {@link #fromEmail}.
	 */
	public String getFromEmail() {
		return fromEmail;
	}

	/**
	 * Bean setter for {@link #fromEmail}. Uses force if the email contains a '@' symbol ({@link #setFromEmail(String, boolean)}).
	 */
	private void setFromEmail(String fromEmail) {
		if (fromEmail != null && fromEmail.contains("@")) {
			setFromEmail(fromEmail, true);
		} else {
			setFromEmail(fromEmail, false);
		}
	}

	/**
	 * @param fromEmail the fromEmail to set
	 * @param force     forces overwriting of the field if already set
	 */
	private void setFromEmail(String fromEmail, boolean force) {
		if ((force || this.fromEmail == null) && fromEmail != null && fromEmail.contains("@")) {
			this.fromEmail = fromEmail;
		}
	}

	/**
	 * Bean getter for {@link #fromName}.
	 */
	public String getFromName() {
		return fromName;
	}

	/**
	 * Bean setter for {@link #fromName}.
	 */
	private void setFromName(String fromName) {
		if (fromName != null) {
			this.fromName = fromName;
		}
	}

	/**
	 * Bean getter for {@link #displayTo}.
	 */
	public String getDisplayTo() {
		return displayTo;
	}

	/**
	 * Bean setter for {@link #displayTo}.
	 */
	private void setDisplayTo(String displayTo) {
		if (displayTo != null) {
			this.displayTo = displayTo;
		}
	}

	/**
	 * Bean getter for {@link #displayCc}.
	 */
	public String getDisplayCc() {
		return displayCc;
	}

	/**
	 * Bean setter for {@link #displayCc}.
	 */
	private void setDisplayCc(String displayCc) {
		if (displayCc != null) {
			this.displayCc = displayCc;
		}
	}

	/**
	 * Bean getter for {@link #displayBcc}.
	 */
	public String getDisplayBcc() {
		return displayBcc;
	}

	/**
	 * Bean setter for {@link #displayBcc}.
	 */
	private void setDisplayBcc(String displayBcc) {
		if (displayBcc != null) {
			this.displayBcc = displayBcc;
		}
	}

	/**
	 * Bean getter for {@link #messageClass}.
	 */
	public String getMessageClass() {
		return messageClass;
	}

	/**
	 * Bean setter for {@link #messageClass}.
	 */
	private void setMessageClass(String messageClass) {
		if (messageClass != null) {
			this.messageClass = messageClass;
		}
	}

	/**
	 * Bean getter for {@link #messageId}.
	 */
	public String getMessageId() {
		return messageId;
	}

	/**
	 * Bean setter for {@link #messageId}.
	 */
	private void setMessageId(String messageId) {
		if (messageId != null) {
			this.messageId = messageId;
		}
	}

	/**
	 * Bean getter for {@link #subject}.
	 */
	public String getSubject() {
		return subject;
	}

	/**
	 * Bean setter for {@link #subject}.
	 */
	private void setSubject(String subject) {
		if (subject != null) {
			this.subject = subject;
		}
	}

	/**
	 * Bean getter for {@link #toEmail}.
	 */
	public String getToEmail() {
		return toEmail;
	}

	/**
	 * Delegates to {@link #setToEmail(String, boolean)} with {@code force = false}.
	 */
	private void setToEmail(String toEmail) {
		setToEmail(toEmail, false);
	}

	/**
	 * @param toEmail the address to set
	 * @param force   forces overwriting of the field if already set
	 */
	private void setToEmail(String toEmail, boolean force) {
		if ((force || this.toEmail == null) && toEmail != null && toEmail.contains("@")) {
			this.toEmail = toEmail;
		}
	}

	/**
	 * Bean getter for {@link #toName}.
	 */
	public String getToName() {
		return toName;
	}

	/**
	 * Bean setter for {@link #toName}.
	 */
	private void setToName(String toName) {
		if (toName != null) {
			this.toName = toName.trim();
		}
	}

	/**
	 * Retrieves the {@link OutlookRecipient} object that represents the TO recipient of the message.
	 *
	 * @return the TO recipient of the message or null in case no {@link OutlookRecipient} was found.
	 */
	public OutlookRecipient getToRecipient() {
		if (getDisplayTo() != null) {
			String recipientKey = getDisplayTo().trim();
			for (OutlookRecipient entry : recipients) {
				String name = entry.getName().trim();
				if (recipientKey.contains(name)) {
					return entry;
				}
			}
		}
		return null;
	}

	/**
	 * Retrieves a list of {@link OutlookRecipient} objects that represent the CC recipients of the message.
	 *
	 * @return the CC recipients of the message.
	 */
	public List<OutlookRecipient> getCcRecipients() {
		List<OutlookRecipient> recipients = new ArrayList<>();
		String recipientKey = getDisplayCc().trim();
		for (OutlookRecipient entry : recipients) {
			String name = entry.getName().trim();
			if (recipientKey.contains(name)) {
				recipients.add(entry);
			}
		}
		return recipients;
	}

	/**
	 * Retrieves a list of {@link OutlookRecipient} objects that represent the BCC recipients of the message.
	 *
	 * @return the BCC recipients of the message.
	 */
	public List<OutlookRecipient> getBccRecipients() {
		List<OutlookRecipient> recipients = new ArrayList<>();
		String recipientKey = getDisplayBcc().trim();
		for (OutlookRecipient entry : recipients) {
			String name = entry.getName().trim();
			if (recipientKey.contains(name)) {
				recipients.add(entry);
			}
		}
		return recipients;
	}

	/**
	 * Bean getter for {@link #bodyText}.
	 */
	@SuppressWarnings("ElementOnlyUsedFromTestCode")
	public String getBodyText() {
		return bodyText;
	}

	/**
	 * Bean setter for {@link #bodyText}.
	 */
	private void setBodyText(String bodyText) {
		if (this.bodyText == null && bodyText != null) {
			this.bodyText = bodyText;
		}
	}

	/**
	 * Bean getter for {@link #bodyRTF}.
	 */
	@SuppressWarnings("ElementOnlyUsedFromTestCode")
	public String getBodyRTF() {
		return bodyRTF;
	}

	/**
	 * @param bodyRTF the bodyRTF to set
	 */
	private void setBodyRTF(Object bodyRTF) {
		// we simply try to decompress the RTF data if it's not compressed, the utils class is able to detect this anyway
		if (this.bodyRTF == null && bodyRTF != null) {
			if (bodyRTF instanceof byte[]) {
				byte[] decompressedBytes = decompressRtfBytes((byte[]) bodyRTF);
				if (decompressedBytes != null) {
					try {
						this.bodyRTF = new String(decompressedBytes, WINDOWS_CHARSET);
						setConvertedBodyHTML(rtf2htmlConverter.rtf2html(this.bodyRTF));
					} catch (UnsupportedEncodingException e) {
						LOGGER.error("Could not convert RTF body to HTML.", e);
					}
				}
			} else {
				LOGGER.warn("Unexpected data type {}", bodyRTF.getClass());
			}
		}
	}

	/**
	 * Bean getter for {@link #bodyHTML}.
	 */
	@SuppressWarnings("ElementOnlyUsedFromTestCode")
	public String getBodyHTML() {
		return bodyHTML;
	}

	/**
	 * Bean getter for {@link #convertedBodyHTML}.
	 */
	@SuppressWarnings("ElementOnlyUsedFromTestCode")
	public String getConvertedBodyHTML() {
		return convertedBodyHTML;
	}

	/**
	 * Bean setter for {@link #convertedBodyHTML}.
	 */
	private void setConvertedBodyHTML(String convertedBodyHTML) {
		this.convertedBodyHTML = convertedBodyHTML;
	}

	/**
	 * @param bodyToSet the bodyHTML to set
	 */
	private void setBodyHTML(String bodyToSet) {
		if (bodyToSet != null) {
			if (!(bodyHTML != null && bodyHTML.length() > bodyToSet.length())) {
				//only if the new body to be set is bigger than the current one
				//thus the short one is most probably wrong
				bodyHTML = bodyToSet;
			}
		}
	}

	/**
	 * Bean getter for {@link #headers}.
	 */
	public String getHeaders() {
		return headers;
	}

	/**
	 * @param headers the headers to set
	 */
	private void setHeaders(String headers) {
		if (headers != null) {
			this.headers = headers;
			// try to parse the date from the headers
			Date d = getDateFromHeaders(headers);
			if (d != null) {
				setDate(d);
			}
			String s = getFromEmailFromHeaders(headers);
			if (s != null) {
				setFromEmail(s);
			}
		}
	}

	/**
	 * Parses the sender's email address from the mail headers.
	 *
	 * @param headers The headers in a single String object
	 * @return The sender's email or null if nothing was found.
	 */
	private static String getFromEmailFromHeaders(String headers) {
		if (headers != null) {
			String[] headerLines = headers.split("\n");
			for (String headerLine : headerLines) {
				if (headerLine.toUpperCase().startsWith("FROM: ")) {
					String[] tokens = headerLine.split(" ");
					for (String potentialFromEmailToken : tokens) {
						if (potentialFromEmailToken.contains("@")) {
							return potentialFromEmailToken.replaceAll("[<>]", "").trim();
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Parses the message date from the mail headers.
	 *
	 * @param headers The headers in a single String object
	 * @return The Date object or null, if no valid Date: has been found
	 */
	private static Date getDateFromHeaders(String headers) {
		if (headers != null) {
			String[] headerLines = headers.split("\n");
			for (String headerLine : headerLines) {
				if (headerLine.toLowerCase().startsWith("date:")) {
					String dateValue = headerLine.substring("Date:".length()).trim();
					SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.ENGLISH);

					// There may be multiple Date: headers. Let's take the first one that can be parsed.
					try {
						Date date = formatter.parse(dateValue);
						if (date != null) {
							return date;
						}
					} catch (ParseException e) {
						LOGGER.debug("Could not parse date {}, moving on to the next date candidate", dateValue, e);
					}
				}
			}
		}
		return null;
	}

	/**
	 * Bean getter for {@link #date}.
	 */
	public Date getDate() {
		return date;
	}

	/**
	 * Bean setter for {@link #date}.
	 */
	private void setDate(Date date) {
		this.date = date;
	}

	/**
	 * Bean getter for {@link #clientSubmitTime}.
	 */
	public Date getClientSubmitTime() {
		return clientSubmitTime;
	}

	private void setClientSubmitTime(String value) {
		if (value != null) {
			Date d = parseDateString(value);
			if (d != null) {
				clientSubmitTime = d;
			}
		}
	}

	/**
	 * Bean getter for {@link #creationDate}.
	 */
	public Date getCreationDate() {
		return creationDate;
	}

	private void setCreationDate(String value) {
		if (value != null) {
			Date d = parseDateString(value);
			if (d != null) {
				creationDate = d;
				setDate(d);
			}
		}
	}

	/**
	 * Bean getter for {@link #lastModificationDate}.
	 */
	public Date getLastModificationDate() {
		return lastModificationDate;
	}

	private void setLastModificationDate(String value) {
		if (value != null) {
			Date d = parseDateString(value);
			if (d != null) {
				lastModificationDate = d;
			}
		}
	}

	/**
	 * This method should no longer be used due to the fact that
	 * message properties are now stored with their keys being represented
	 * as integers.
	 *
	 * @return All available keys properties have been found for.
	 */
	@Deprecated
	public Set<String> getProperties() {
		return getPropertiesAsHex();
	}

	/**
	 * This method provides a convenient way of retrieving
	 * property keys for all guys that like to stick to hex values.
	 * <br>Note that this method includes parsing of string values
	 * to integers which will be less efficient than using
	 * {@link #getPropertyCodes()}.
	 *
	 * @return All available keys properties have been found for.
	 */
	public Set<String> getPropertiesAsHex() {
		Set<Integer> keySet = properties.keySet();
		Set<String> result = new HashSet<>();
		for (Integer k : keySet) {
			String s = convertToHex(k);
			result.add(s);
		}

		return result;
	}

	/**
	 * This method should no longer be used due to the fact that message properties are now stored with their keys being
	 * represented as integers. <br>
	 * <br>
	 * Please refer to {@link #getPropertyCodes()} for dealing with integer based keys.
	 *
	 * @return The value for the requested property.
	 */
	@Deprecated
	public Object getProperty(String name) {
		return getPropertyFromHex(name);
	}

	/**
	 * This method provides a convenient way of retrieving properties for all guys that like to stick to hex values. <br>
	 * Note that this method includes parsing of string values to integers which will be less efficient than using
	 * {@link #getPropertyValue(Integer)}.
	 *
	 * @param name The hex notation of the property to be retrieved.
	 * @return The value for the requested property.
	 */
	private Object getPropertyFromHex(String name) {
		try {
			return getPropertyValue(Integer.parseInt(name, 16));
		} catch (NumberFormatException e) {
			LOGGER.error("Could not parse integer: {}", name, e);
		}
		return getPropertyValue(-1);
	}

	/**
	 * This method returns a list of all available properties.
	 *
	 * @return All available keys properties have been found for.
	 */
	public Set<Integer> getPropertyCodes() {
		return properties.keySet();
	}

	/**
	 * This method retrieves the value for a specific property.
	 * <p>
	 * <b>NOTE:</b> You can also use fields defined within {@link MAPIProperty} to easily read certain properties.
	 *
	 * @param code The key for the property to be retrieved.
	 * @return The value of the specified property.
	 */
	private Object getPropertyValue(Integer code) {
		return properties.get(code);
	}

	/**
	 * Generates a string that can be used to debug the properties of the msg.
	 *
	 * @return A property listing holding hexadecimal, decimal and string representations of properties and their values.
	 */
	public String getPropertyListing() {
		StringBuilder sb = new StringBuilder();
		for (Integer propCode : getPropertyCodes()) {
			Object value = getPropertyValue(propCode);
			String hexCode = "0x" + convertToHex(propCode);
			sb.append(hexCode).append(" / ").append(propCode);
			sb.append(": ").append(value);
			sb.append("\n");
		}
		return sb.toString();
	}

	/**
	 * Converts a given integer to hex notation without leading '0x'.
	 *
	 * @param propCode The value to be formatted.
	 * @return A hex formatted number.
	 */
	private String convertToHex(Integer propCode) {
		return String.format("%04x", propCode);
	}

	/**
	 * Bean getter for {@link #replyToEmail}.
	 */
	@SuppressWarnings("ElementOnlyUsedFromTestCode")
	public String getReplyToEmail() {
		return replyToEmail;
	}

	/**
	 * Bean setter for {@link #replyToEmail}.
	 */
	public void setReplyToEmail(String replyToEmail) {
		this.replyToEmail = replyToEmail;
	}

	/**
	 * Bean getter for {@link #replyToName}.
	 */
	@SuppressWarnings("ElementOnlyUsedFromTestCode")
	public String getReplyToName() {
		return replyToName;
	}

	/**
	 * Bean setter for {@link #replyToName}.
	 */
	public void setReplyToName(String replyToName) {
		this.replyToName = replyToName;
	}
}