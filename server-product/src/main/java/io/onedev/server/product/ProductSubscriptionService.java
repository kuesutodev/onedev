package io.onedev.server.product;

import javax.inject.Singleton;

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.jspecify.annotations.Nullable;

import io.onedev.server.SubscriptionService;

@Singleton
public class ProductSubscriptionService implements SubscriptionService {

	@Override
	public boolean isSubscriptionActive() {
		return false;
	}

	@Override
	@Nullable
	public String getLicensee() {
		return null;
	}

	@Override
	public Component renderSupportRequestLink(String componentId) {
		return new WebMarkupContainer(componentId).setVisible(false);
	}

}