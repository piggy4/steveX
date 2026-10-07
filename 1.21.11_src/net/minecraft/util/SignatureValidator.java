package net.minecraft.util;

import com.mojang.authlib.yggdrasil.ServicesKeyInfo;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import com.mojang.authlib.yggdrasil.ServicesKeyType;
import com.mojang.logging.LogUtils;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Collection;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public interface SignatureValidator {
	SignatureValidator NO_VALIDATION = (signatureUpdater, bs) -> true;
	Logger LOGGER = LogUtils.getLogger();

	boolean validate(SignatureUpdater signatureUpdater, byte[] bs);

	default boolean validate(byte[] bs, byte[] cs) {
		return this.validate(output -> output.update(bs), cs);
	}

	private static boolean verifySignature(SignatureUpdater signatureUpdater, byte[] bs, Signature signature) throws SignatureException {
		signatureUpdater.update(signature::update);
		return signature.verify(bs);
	}

	static SignatureValidator from(PublicKey publicKey, String string) {
		return (signatureUpdater, bs) -> {
			try {
				Signature signature = Signature.getInstance(string);
				signature.initVerify(publicKey);
				return verifySignature(signatureUpdater, bs, signature);
			} catch (Exception exception) {
				LOGGER.error("Failed to verify signature", exception);
				return false;
			}
		};
	}

	static @Nullable SignatureValidator from(ServicesKeySet servicesKeySet, ServicesKeyType servicesKeyType) {
		Collection<ServicesKeyInfo> collection = servicesKeySet.keys(servicesKeyType);
		return collection.isEmpty() ? null : (signatureUpdater, bs) -> collection.stream().anyMatch(servicesKeyInfo -> {
			Signature signature = servicesKeyInfo.signature();

			try {
				return verifySignature(signatureUpdater, bs, signature);
			} catch (SignatureException signatureException) {
				LOGGER.error("Failed to verify Services signature", signatureException);
				return false;
			}
		});
	}
}
