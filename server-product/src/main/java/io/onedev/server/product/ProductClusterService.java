package io.onedev.server.product;

import static io.onedev.server.replica.ProjectReplica.Type.PRIMARY;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.jspecify.annotations.Nullable;

import com.hazelcast.cluster.Member;
import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.IAtomicLong;

import io.onedev.server.ServerConfig;
import io.onedev.server.cluster.ClusterService;
import io.onedev.server.cluster.ClusterTask;
import io.onedev.server.replica.ProjectReplica;

@Singleton
public class ProductClusterService implements ClusterService {

	private final ServerConfig serverConfig;

	private final String localServerAddress;

	private final String credential = UUID.randomUUID().toString();

	private volatile HazelcastInstance hazelcastInstance;

	@Inject
	public ProductClusterService(ServerConfig serverConfig) {
		this.serverConfig = serverConfig;
		localServerAddress = serverConfig.getClusterIp() + ":" + serverConfig.getClusterPort();
	}

	@Override
	public void start() {
		if (hazelcastInstance == null) {
			var config = new Config();
			config.setClusterName("gsg-onedev-" + serverConfig.getClusterPort());
			NetworkConfig networkConfig = config.getNetworkConfig();
			networkConfig.setPort(serverConfig.getClusterPort());
			networkConfig.setPortAutoIncrement(false);
			JoinConfig join = networkConfig.getJoin();
			join.getAutoDetectionConfig().setEnabled(false);
			join.getMulticastConfig().setEnabled(false);
			join.getTcpIpConfig().setEnabled(false);
			hazelcastInstance = Hazelcast.newHazelcastInstance(config);
		}
	}

	@Override
	public void postStart() {
	}

	@Override
	public void preStop() {
	}

	@Override
	public void stop() {
		if (hazelcastInstance != null) {
			hazelcastInstance.shutdown();
			hazelcastInstance = null;
		}
	}

	@Override
	public Collection<String> getOnlineServers() {
		return List.of(localServerAddress);
	}

	@Override
	public boolean isLeaderServer() {
		return true;
	}

	@Override
	@Nullable
	public HazelcastInstance getHazelcastInstance() {
		return hazelcastInstance;
	}

	@Override
	public void initWithLead(IAtomicLong data, Callable<Long> initializer) {
		if (data.get() == 0) {
			try {
				data.compareAndSet(0, initializer.call());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public <T> T runOnServer(Member server, ClusterTask<T> task) {
		return call(task);
	}

	@Override
	public <T> T runOnServer(String serverAddress, ClusterTask<T> task) {
		return call(task);
	}

	@Override
	public <T> Map<String, T> runOnAllServers(ClusterTask<T> task) {
		var results = new LinkedHashMap<String, T>();
		results.put(localServerAddress, call(task));
		return results;
	}

	@Override
	public <T> Map<String, T> runOnServers(Collection<String> servers, ClusterTask<T> task) {
		var results = new LinkedHashMap<String, T>();
		if (servers.contains(localServerAddress))
			results.put(localServerAddress, call(task));
		return results;
	}

	@Override
	public <T> Map<String, Future<T>> submitToAllServers(ClusterTask<T> task) {
		return Map.of(localServerAddress, completed(call(task)));
	}

	@Override
	public <T> Map<String, Future<T>> submitToServers(Collection<String> servers, ClusterTask<T> task) {
		if (servers.contains(localServerAddress))
			return Map.of(localServerAddress, completed(call(task)));
		else
			return Map.of();
	}

	@Override
	public <T> Future<T> submitToServer(String serverAddress, ClusterTask<T> task) {
		return completed(call(task));
	}

	@Override
	public <T> Future<T> submitToServer(Member server, ClusterTask<T> task) {
		return completed(call(task));
	}

	@Override
	public String getServerUrl(String serverAddress) {
		return "http://" + getServerHost(serverAddress) + ":" + getHttpPort(serverAddress);
	}

	@Override
	public int getHttpPort(String serverAddress) {
		return serverConfig.getHttpPort();
	}

	@Override
	public int getSshPort(String serverAddress) {
		return serverConfig.getSshPort();
	}

	@Override
	public String getServerHost(String serverAddress) {
		var host = serverConfig.getHttpHost();
		if (host.equals("0.0.0.0") || host.equals("::") || host.equals("::0"))
			return "127.0.0.1";
		return host;
	}

	@Override
	public String getServerName(String serverAddress) {
		return serverConfig.getServerName();
	}

	@Override
	public String getServerAddress(Member server) {
		return localServerAddress;
	}

	@Override
	public String getLeaderServerAddress() {
		return localServerAddress;
	}

	@Override
	public String getLocalServerAddress() {
		return localServerAddress;
	}

	@Override
	public String getCredential() {
		return credential;
	}

	@Override
	@Nullable
	public Member getServer(String serverAddress, boolean mustExist) {
		if (serverAddress.equals(localServerAddress) && hazelcastInstance != null)
			return hazelcastInstance.getCluster().getLocalMember();
		if (mustExist)
			throw new IllegalStateException("Server not found: " + serverAddress);
		return null;
	}

	@Override
	public List<String> getServerAddresses() {
		return new ArrayList<>(List.of(localServerAddress));
	}

	@Override
	public void redistributeProjects(Map<Long, LinkedHashMap<String, ProjectReplica>> replicas) {
		for (var entry : replicas.entrySet())
			replicas.put(entry.getKey(), localReplicas(entry.getValue()));
	}

	@Override
	public LinkedHashMap<String, ProjectReplica> addProject(Map<Long, LinkedHashMap<String, ProjectReplica>> replicas,
			Long projectId) {
		return localReplicas(replicas.get(projectId));
	}

	@Override
	public boolean isClusteringSupported() {
		return false;
	}

	private LinkedHashMap<String, ProjectReplica> localReplicas(@Nullable LinkedHashMap<String, ProjectReplica> existing) {
		var replicas = new LinkedHashMap<String, ProjectReplica>();
		ProjectReplica replica;
		if (existing != null && existing.containsKey(localServerAddress)) {
			replica = existing.get(localServerAddress);
		} else {
			replica = new ProjectReplica();
			replica.setType(PRIMARY);
			replica.setVersion(0);
		}
		replica.setType(PRIMARY);
		replicas.put(localServerAddress, replica);
		return replicas;
	}

	private <T> T call(ClusterTask<T> task) {
		try {
			return task.call();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private <T> Future<T> completed(T value) {
		return CompletableFuture.completedFuture(value);
	}

}