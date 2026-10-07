package net.minecraft.server.jsonrpc.internalapi;

import java.util.stream.Stream;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.jsonrpc.JsonRpcLogger;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.jsonrpc.methods.GameRulesService;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;

public class MinecraftGameRuleServiceImpl implements MinecraftGameRuleService {
	private final DedicatedServer server;
	private final GameRules gameRules;
	private final JsonRpcLogger jsonrpcLogger;

	public MinecraftGameRuleServiceImpl(DedicatedServer dedicatedServer, JsonRpcLogger jsonRpcLogger) {
		this.server = dedicatedServer;
		this.gameRules = dedicatedServer.getWorldData().getGameRules();
		this.jsonrpcLogger = jsonRpcLogger;
	}

	@Override
	public <T> GameRulesService.GameRuleUpdate<T> updateGameRule(GameRulesService.GameRuleUpdate<T> gameRuleUpdate, ClientInfo clientInfo) {
		GameRule<T> gameRule = gameRuleUpdate.gameRule();
		T object = this.gameRules.get(gameRule);
		T object2 = gameRuleUpdate.value();
		this.gameRules.set(gameRule, object2, this.server);
		this.jsonrpcLogger.log(clientInfo, "Game rule '{}' updated from '{}' to '{}'", gameRule.id(), gameRule.serialize(object), gameRule.serialize(object2));
		return gameRuleUpdate;
	}

	@Override
	public <T> GameRulesService.GameRuleUpdate<T> getTypedRule(GameRule<T> gameRule, T object) {
		return new GameRulesService.GameRuleUpdate<>(gameRule, object);
	}

	@Override
	public Stream<GameRule<?>> getAvailableGameRules() {
		return this.gameRules.availableRules();
	}

	@Override
	public <T> T getRuleValue(GameRule<T> gameRule) {
		return this.gameRules.get(gameRule);
	}
}
