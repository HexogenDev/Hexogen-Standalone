package net.hexogendev.hexogen.standalone.management;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import net.hexogendev.hexogen.api.Server;
import net.hexogendev.hexogen.api.management.UserManager;

public class HexoUserManager implements UserManager {

	private Server server;
	private List<UUID> uuids;

	public HexoUserManager(Server server) {
		this.server = server;
		this.uuids = new CopyOnWriteArrayList<UUID>();
	}

	@Override
	public boolean checkPlayer(UUID uuid) {
		return uuids.contains(uuid);
	}

	@Override
	public void addPlayer(UUID uuid) {
		if (!checkPlayer(uuid)) {
			uuids.add(uuid);
		}
	}

	@Override
	public void removePlayer(UUID uuid) {
		if (checkPlayer(uuid)) {
			uuids.remove(uuid);
		}
	}

	@Override
	public Server getServer() {
		return server;
	}

	@Override
	public List<UUID> getUUID() {
		return uuids;
	}

}
