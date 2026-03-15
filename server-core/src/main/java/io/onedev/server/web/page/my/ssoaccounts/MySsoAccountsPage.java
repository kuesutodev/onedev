package io.onedev.server.web.page.my.ssoaccounts;

import static io.onedev.server.model.User.Type.ORDINARY;
import static io.onedev.server.web.translation.Translation._T;
import static io.onedev.server.web.page.security.SsoProcessPage.MOUNT_PATH;
import static io.onedev.server.web.page.security.SsoProcessPage.STAGE_INITIATE;
import static io.onedev.server.web.page.security.SsoProcessPage.SESSION_ATTR_LINK_MODE;

import java.text.MessageFormat;

import javax.inject.Inject;

import org.apache.wicket.Component;
import org.apache.wicket.Session;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.request.flow.RedirectToUrlException;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import io.onedev.server.model.SsoProvider;
import io.onedev.server.model.User;
import io.onedev.server.service.SettingService;
import io.onedev.server.service.SsoProviderService;
import io.onedev.server.web.component.user.ssoaccount.SsoAccountListPanel;
import io.onedev.server.web.page.my.MyPage;

public class MySsoAccountsPage extends MyPage {
	
	@Inject
	private SsoProviderService ssoProviderService;
	
	@Inject
	private SettingService settingService;
		
	public MySsoAccountsPage(PageParameters params) {
		super(params);
		if (getUser().isDisabled() || getUser().getType() != ORDINARY)
			throw new IllegalStateException();
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();
		
		// Add SSO provider buttons for linking accounts
		String serverUrl = settingService.getSystemSetting().getServerUrl();
		var ssoProviders = ssoProviderService.query();
		
		ListView<SsoProvider> linkButtonsView = new ListView<SsoProvider>("linkButtons", ssoProviders) {
			@Override
			protected void populateItem(ListItem<SsoProvider> item) {
				SsoProvider provider = item.getModelObject();
				String providerName = provider.getName();
				String linkUrl = serverUrl + "/" + MOUNT_PATH + "/" + STAGE_INITIATE + "/" + providerName;
				
				Link<Void> linkButton = new Link<Void>("linkButton") {
					@Override
					public void onClick() {
						// Set link mode in session so SsoProcessPage knows to link instead of login
						Session.get().bind();
						Session.get().setAttribute(SESSION_ATTR_LINK_MODE, getLoginUser().getId());
						throw new RedirectToUrlException(linkUrl);
					}
				};
				linkButton.add(new Label("label", MessageFormat.format(_T("Link {0}"), providerName)));
				item.add(linkButton);
			}
		};
		add(linkButtonsView.setVisible(!ssoProviders.isEmpty()));
            
		add(new SsoAccountListPanel("accountList", new LoadableDetachableModel<User>() {
			
		    @Override
		    protected User load() {
		    	return getLoginUser();
		    }
		    
		}));
	}

	@Override
	protected Component newTopbarTitle(String componentId) {
		return new Label(componentId, _T("My SSO Accounts"));
	}
	
}
