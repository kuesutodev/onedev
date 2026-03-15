package io.onedev.server.model.support.administration.sso;

import io.onedev.server.OneDev;
import io.onedev.server.model.SsoAccount;
import io.onedev.server.model.User;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.service.SsoProviderService;

public class SsoAccountHelper {

	public static boolean hasLinkedWallet(User user) {
		return countLinkedWallets(user) != 0;
	}

	public static int countLinkedWallets(User user) {
		return (int) user.getSsoAccounts().stream()
				.filter(SsoAccountHelper::isWalletAccount)
				.count();
	}

	public static boolean isWalletAccount(SsoAccount ssoAccount) {
		return ssoAccount.getProvider().getConnector().isWalletConnector();
	}

	public static boolean wouldLeaveAdministratorWithoutWallet(User user, SsoAccount ssoAccount) {
		return SecurityUtils.isAdministrator(SecurityUtils.asSubject(user))
				&& isWalletAccount(ssoAccount)
				&& countLinkedWallets(user) <= 1;
	}

	public static boolean isPasswordAuthenticationDisabled() {
		try {
			var ssoProviderService = OneDev.getInstance(SsoProviderService.class);
			for (var provider : ssoProviderService.query()) {
				if (provider.getConnector().isPasswordAuthenticationDisabled())
					return true;
			}
		} catch (Exception ignored) {
		}
		return false;
	}

	private SsoAccountHelper() {
	}

}