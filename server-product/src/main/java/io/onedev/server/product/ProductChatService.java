package io.onedev.server.product;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.wicket.Page;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.jspecify.annotations.Nullable;

import io.onedev.server.ai.ChatService;
import io.onedev.server.model.Chat;
import io.onedev.server.model.ChatMessage;
import io.onedev.server.model.User;
import io.onedev.server.persistence.dao.Dao;
import io.onedev.server.persistence.dao.EntityCriteria;
import io.onedev.server.service.support.ChatResponding;
import io.onedev.server.web.WebSession;

@Singleton
public class ProductChatService implements ChatService {

	private static final String UNAVAILABLE_MESSAGE = "AI chat is not available in this package build.";

	private final Dao dao;

	private final AtomicLong nextAnonymousChatId = new AtomicLong(1);

	private final AtomicLong nextAnonymousChatMessageId = new AtomicLong(1);

	@Inject
	public ProductChatService(Dao dao) {
		this.dao = dao;
	}

	@Override
	public List<Chat> query(User user, User ai, String term, int count) {
		var criteria = EntityCriteria.of(Chat.class);
		criteria.add(Restrictions.eq(Chat.PROP_USER, user));
		criteria.add(Restrictions.eq(Chat.PROP_AI, ai));
		if (!term.isBlank())
			criteria.add(Restrictions.ilike(Chat.PROP_TITLE, term, MatchMode.ANYWHERE));
		criteria.addOrder(Order.desc(Chat.PROP_DATE));
		return dao.query(criteria, 0, count);
	}

	@Override
	public void createOrUpdate(Chat chat) {
		dao.persist(chat);
	}

	@Override
	public void sendRequest(Page page, ChatMessage request) {
		var chat = request.getChat();
		if (chat != null && chat.getId() != null) {
			var response = new ChatMessage();
			response.setChat(chat);
			response.setError(true);
			response.setContent(UNAVAILABLE_MESSAGE);
			chat.getMessages().add(response);
			dao.persist(response);
		}
	}

	@Override
	@Nullable
	public ChatResponding getResponding(WebSession session, Chat chat) {
		return null;
	}

	@Override
	public long nextAnonymousChatId() {
		return nextAnonymousChatId.getAndIncrement();
	}

	@Override
	public long nextAnonymousChatMessageId() {
		return nextAnonymousChatMessageId.getAndIncrement();
	}

	@Override
	@Nullable
	public Chat get(Long entityId) {
		return dao.get(Chat.class, entityId);
	}

	@Override
	public Chat load(Long entityId) {
		return dao.load(Chat.class, entityId);
	}

	@Override
	public void delete(Chat entity) {
		dao.remove(entity);
	}

	@Override
	public List<Chat> query(EntityCriteria<Chat> criteria, int firstResult, int maxResults) {
		return dao.query(criteria, firstResult, maxResults);
	}

	@Override
	public List<Chat> query(EntityCriteria<Chat> criteria) {
		return dao.query(criteria);
	}

	@Override
	public List<Chat> query(boolean cacheable) {
		return dao.query(Chat.class, cacheable);
	}

	@Override
	public List<Chat> query() {
		return dao.query(Chat.class);
	}

	@Override
	public int count(boolean cacheable) {
		return dao.count(Chat.class, cacheable);
	}

	@Override
	public int count() {
		return dao.count(Chat.class);
	}

	@Override
	public EntityCriteria<Chat> newCriteria() {
		return EntityCriteria.of(Chat.class);
	}

	@Override
	@Nullable
	public Chat find(EntityCriteria<Chat> entityCriteria) {
		return dao.find(entityCriteria);
	}

	@Override
	public int count(EntityCriteria<Chat> entityCriteria) {
		return dao.count(entityCriteria);
	}

}