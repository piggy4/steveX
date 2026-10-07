package net.minecraft.server.jsonrpc.methods;

public record ClientInfo(Integer connectionId) {
	public static ClientInfo of(Integer integer) {
		return new ClientInfo(integer);
	}
}
