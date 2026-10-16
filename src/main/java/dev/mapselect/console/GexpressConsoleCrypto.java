package dev.mapselect.console;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * End-to-end encryption for owner-console traffic sent directly over an allocated host port.
 *
 * <p>The random owner token is used as a pre-shared secret. Separate AES-256-CBC and
 * HMAC-SHA-256 keys are derived with HKDF. Every envelope is authenticated before it is
 * decrypted; timestamps and request nonces are checked by the bridge to reject replays.</p>
 */
final class GexpressConsoleCrypto {
	static final int VERSION = 1;
	static final long MAX_CLOCK_SKEW_SECONDS = 120L;
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final byte[] HKDF_SALT = "GExpress owner bridge direct v1"
		.getBytes(StandardCharsets.UTF_8);
	private static final String ENCRYPTION_INFO = "gexpress-direct-encryption-v1";
	private static final String AUTHENTICATION_INFO = "gexpress-direct-authentication-v1";

	private GexpressConsoleCrypto() {}

	static OpenedRequest openRequest(String token, String json, long nowSeconds)
		throws GeneralSecurityException {
		Envelope envelope = parseEnvelope(json);
		validateTimestamp(envelope.timestamp, nowSeconds);
		byte[] plaintext = open(token, envelope, "");
		try {
			Request request = GSON.fromJson(new String(plaintext, StandardCharsets.UTF_8), Request.class);
			if (request == null || request.method == null || request.path == null) {
				throw new GeneralSecurityException("Encrypted request is incomplete.");
			}
			return new OpenedRequest(envelope.nonce, envelope.timestamp, request.method, request.path,
				request.body);
		} catch (JsonParseException e) {
			throw new GeneralSecurityException("Encrypted request is invalid.", e);
		} finally {
			Arrays.fill(plaintext, (byte) 0);
		}
	}

	static String sealResponse(String token, String requestNonce, int status, String body, String message)
		throws GeneralSecurityException {
		Response response = new Response();
		response.status = status;
		response.body = body == null ? "" : body;
		response.message = message == null ? "" : message;
		return seal(token, GSON.toJson(response), requestNonce, System.currentTimeMillis() / 1000L,
			randomBytes(16), randomBytes(16));
	}

	static String sealRequestForTest(String token, String method, String path, String body,
		long timestamp, byte[] nonce, byte[] iv) throws GeneralSecurityException {
		Request request = new Request();
		request.method = method;
		request.path = path;
		request.body = body;
		return seal(token, GSON.toJson(request), "", timestamp, nonce, iv);
	}

	static OpenedResponse openResponseForTest(String token, String requestNonce, String json,
		long nowSeconds) throws GeneralSecurityException {
		Envelope envelope = parseEnvelope(json);
		validateTimestamp(envelope.timestamp, nowSeconds);
		if (!MessageDigest.isEqual(value(requestNonce).getBytes(StandardCharsets.UTF_8),
			value(envelope.replyTo).getBytes(StandardCharsets.UTF_8))) {
			throw new GeneralSecurityException("Encrypted response does not match its request.");
		}
		byte[] plaintext = open(token, envelope, requestNonce);
		try {
			Response response = GSON.fromJson(new String(plaintext, StandardCharsets.UTF_8), Response.class);
			if (response == null) throw new GeneralSecurityException("Encrypted response is invalid.");
			return new OpenedResponse(response.status, response.body, response.message);
		} catch (JsonParseException e) {
			throw new GeneralSecurityException("Encrypted response is invalid.", e);
		} finally {
			Arrays.fill(plaintext, (byte) 0);
		}
	}

	private static String seal(String token, String plaintext, String replyTo, long timestamp,
		byte[] nonce, byte[] iv) throws GeneralSecurityException {
		if (nonce == null || nonce.length != 16 || iv == null || iv.length != 16) {
			throw new GeneralSecurityException("Secure-envelope nonce or IV is invalid.");
		}
		DerivedKeys keys = deriveKeys(token);
		byte[] clear = plaintext.getBytes(StandardCharsets.UTF_8);
		try {
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keys.encryption, "AES"),
				new IvParameterSpec(iv));
			byte[] encrypted = cipher.doFinal(clear);
			Envelope envelope = new Envelope();
			envelope.version = VERSION;
			envelope.timestamp = timestamp;
			envelope.nonce = Base64.getEncoder().encodeToString(nonce);
			envelope.replyTo = value(replyTo);
			envelope.iv = Base64.getEncoder().encodeToString(iv);
			envelope.ciphertext = Base64.getEncoder().encodeToString(encrypted);
			envelope.mac = Base64.getEncoder().encodeToString(mac(keys.authentication, canonical(envelope)));
			Arrays.fill(encrypted, (byte) 0);
			return GSON.toJson(envelope);
		} finally {
			Arrays.fill(clear, (byte) 0);
			keys.clear();
		}
	}

	private static byte[] open(String token, Envelope envelope, String expectedReplyTo)
		throws GeneralSecurityException {
		if (envelope.version != VERSION) {
			throw new GeneralSecurityException("Unsupported secure-envelope version.");
		}
		if (!MessageDigest.isEqual(value(expectedReplyTo).getBytes(StandardCharsets.UTF_8),
			value(envelope.replyTo).getBytes(StandardCharsets.UTF_8))) {
			throw new GeneralSecurityException("Secure-envelope reply binding is invalid.");
		}
		byte[] nonce = decode(envelope.nonce);
		byte[] iv = decode(envelope.iv);
		byte[] ciphertext = decode(envelope.ciphertext);
		byte[] suppliedMac = decode(envelope.mac);
		if (nonce.length != 16 || iv.length != 16 || suppliedMac.length != 32
			|| ciphertext.length == 0 || ciphertext.length % 16 != 0) {
			throw new GeneralSecurityException("Secure-envelope fields are invalid.");
		}
		DerivedKeys keys = deriveKeys(token);
		try {
			byte[] expectedMac = mac(keys.authentication, canonical(envelope));
			boolean authenticated = MessageDigest.isEqual(expectedMac, suppliedMac);
			Arrays.fill(expectedMac, (byte) 0);
			if (!authenticated) throw new GeneralSecurityException("Secure-envelope authentication failed.");
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keys.encryption, "AES"),
				new IvParameterSpec(iv));
			return cipher.doFinal(ciphertext);
		} finally {
			Arrays.fill(nonce, (byte) 0);
			Arrays.fill(iv, (byte) 0);
			Arrays.fill(ciphertext, (byte) 0);
			Arrays.fill(suppliedMac, (byte) 0);
			keys.clear();
		}
	}

	private static Envelope parseEnvelope(String json) throws GeneralSecurityException {
		try {
			Envelope envelope = GSON.fromJson(json, Envelope.class);
			if (envelope == null || envelope.nonce == null || envelope.iv == null
				|| envelope.ciphertext == null || envelope.mac == null) {
				throw new GeneralSecurityException("Secure envelope is incomplete.");
			}
			return envelope;
		} catch (JsonParseException e) {
			throw new GeneralSecurityException("Secure envelope is invalid.", e);
		}
	}

	private static void validateTimestamp(long timestamp, long nowSeconds) throws GeneralSecurityException {
		if (timestamp < nowSeconds - MAX_CLOCK_SKEW_SECONDS
			|| timestamp > nowSeconds + MAX_CLOCK_SKEW_SECONDS) {
			throw new GeneralSecurityException("Secure-envelope timestamp is outside the allowed window.");
		}
	}

	private static DerivedKeys deriveKeys(String token) throws GeneralSecurityException {
		byte[] tokenBytes = value(token).getBytes(StandardCharsets.UTF_8);
		byte[] prk = hmac(HKDF_SALT, tokenBytes);
		try {
			return new DerivedKeys(expand(prk, ENCRYPTION_INFO), expand(prk, AUTHENTICATION_INFO));
		} finally {
			Arrays.fill(tokenBytes, (byte) 0);
			Arrays.fill(prk, (byte) 0);
		}
	}

	private static byte[] expand(byte[] prk, String info) throws GeneralSecurityException {
		byte[] text = info.getBytes(StandardCharsets.UTF_8);
		byte[] input = Arrays.copyOf(text, text.length + 1);
		input[input.length - 1] = 1;
		try {
			return hmac(prk, input);
		} finally {
			Arrays.fill(input, (byte) 0);
		}
	}

	private static byte[] mac(byte[] key, byte[] input) throws GeneralSecurityException {
		return hmac(key, input);
	}

	private static byte[] hmac(byte[] key, byte[] input) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key, "HmacSHA256"));
		return mac.doFinal(input);
	}

	private static byte[] canonical(Envelope envelope) {
		return (envelope.version + "\n" + envelope.timestamp + "\n" + value(envelope.nonce) + "\n"
			+ value(envelope.replyTo) + "\n" + value(envelope.iv) + "\n"
			+ value(envelope.ciphertext)).getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] decode(String value) throws GeneralSecurityException {
		try {
			return Base64.getDecoder().decode(value(value));
		} catch (IllegalArgumentException e) {
			throw new GeneralSecurityException("Secure-envelope encoding is invalid.", e);
		}
	}

	private static byte[] randomBytes(int length) {
		byte[] bytes = new byte[length];
		RANDOM.nextBytes(bytes);
		return bytes;
	}

	private static String value(String text) {
		return text == null ? "" : text;
	}

	static final class OpenedRequest {
		final String nonce;
		final long timestamp;
		final String method;
		final String path;
		final String body;

		OpenedRequest(String nonce, long timestamp, String method, String path, String body) {
			this.nonce = nonce;
			this.timestamp = timestamp;
			this.method = method;
			this.path = path;
			this.body = body;
		}
	}

	static final class OpenedResponse {
		final int status;
		final String body;
		final String message;

		OpenedResponse(int status, String body, String message) {
			this.status = status;
			this.body = body;
			this.message = message;
		}
	}

	private static final class Envelope {
		int version;
		long timestamp;
		String nonce;
		String replyTo;
		String iv;
		String ciphertext;
		String mac;
	}

	private static final class Request {
		String method;
		String path;
		String body;
	}

	private static final class Response {
		int status;
		String body;
		String message;
	}

	private static final class DerivedKeys {
		final byte[] encryption;
		final byte[] authentication;

		DerivedKeys(byte[] encryption, byte[] authentication) {
			this.encryption = encryption;
			this.authentication = authentication;
		}

		void clear() {
			Arrays.fill(encryption, (byte) 0);
			Arrays.fill(authentication, (byte) 0);
		}
	}
}
