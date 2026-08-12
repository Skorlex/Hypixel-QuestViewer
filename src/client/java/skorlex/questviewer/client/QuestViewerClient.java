package skorlex.questviewer.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import skorlex.questviewer.QuestViewer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuestViewerClient implements ClientModInitializer {

	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(QuestViewer.MOD_ID, "quests")
	);

	public static KeyMapping checkQuestsKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping(
					"key.questviewer.check_quests",
					InputConstants.Type.KEYSYM,
					InputConstants.KEY_K,
					CATEGORY
			)
	);

	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.ALWAYS)
			.build();

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (checkQuestsKey.consumeClick()) {
				if (client.player != null) {
					String selfUuidOrName = client.player.getUUID().toString().replace("-", "");
					fetchData(client, selfUuidOrName, "current", false);
				}
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			String[] aliases = {"q", "quest", "quests"};
			for (String alias : aliases) {
				dispatcher.register(
						ClientCommands.literal(alias)
								.executes(context -> {
									processArgs(Minecraft.getInstance(), new String[0]);
									return 1;
								})
								.then(ClientCommands.argument("action", StringArgumentType.word())
										.suggests((context, builder) -> {
											String[] suggestions = {"daily", "weekly", "leaderboard", "stats", "site", "games", "help"};
											String remaining = builder.getRemaining().toLowerCase();
											for (String suggestion : suggestions) {
												if (suggestion.startsWith(remaining)) {
													builder.suggest(suggestion);
												}
											}
											return builder.buildFuture();
										})
										.executes(context -> {
											String action = StringArgumentType.getString(context, "action");
											processArgs(Minecraft.getInstance(), new String[]{action});
											return 1;
										})
										.then(ClientCommands.argument("game_or_player", StringArgumentType.word())
												.executes(context -> {
													String action = StringArgumentType.getString(context, "action");
													String target = StringArgumentType.getString(context, "game_or_player");
													processArgs(Minecraft.getInstance(), new String[]{action, target});
													return 1;
												})
												.then(ClientCommands.argument("player", StringArgumentType.word())
														.executes(context -> {
															String action = StringArgumentType.getString(context, "action");
															String target = StringArgumentType.getString(context, "game_or_player");
															String player = StringArgumentType.getString(context, "player");
															processArgs(Minecraft.getInstance(), new String[]{action, target, player});
															return 1;
														})
												)
										)
								)
				);
			}
		});
	}

	private void processArgs(Minecraft client, String[] args) {
		client.execute(() -> {
			if (client.player == null) return;
			String selfUuidOrName = client.player.getUUID().toString().replace("-", "");

			if (args.length == 0) {
				printHelp(client);
			} else if (args.length == 1) {
				switch (args[0].toLowerCase()) {
					case "api":
						client.player.sendSystemMessage(Component.literal("§cYour API key is no longer needed to use this mod :)"));
						break;
					case "help":
						printHelp(client);
						break;
					case "games":
						fetchGames(client);
						break;
					case "lb":
					case "leaderboard":
						fetchLeaderboard(client, 1);
						break;
					case "info":
					case "i":
					case "stats":
						fetchStats(client, selfUuidOrName);
						break;
					case "weekly":
					case "w":
						fetchData(client, selfUuidOrName, "current", true);
						break;
					case "daily":
					case "d":
						fetchData(client, selfUuidOrName, "current", false);
						break;
					case "site":
					case "s":
						printSite(client, selfUuidOrName);
						break;
					default:
						client.player.sendSystemMessage(Component.literal("§cError: Could not register argument: " + args[0]));
						client.player.sendSystemMessage(Component.literal("§cType '/q help' for a list of commands"));
				}
			} else if (args.length == 2) {
				switch (args[0].toLowerCase()) {
					case "lb":
					case "leaderboard":
						int page = 1;
						try {
							page = Integer.parseInt(args[1]);
						} catch (NumberFormatException e) {
							// Defaults to page 1 if they type something like "/q leaderboard abc"
						}

						if (page > 10) {
							client.player.sendSystemMessage(Component.literal("§cYou cannot go higher than page 10."));
							break;
						} else if (page < 1) {
							page = 1;
						}

						fetchLeaderboard(client, page);
						break;
					case "site":
					case "s":
						printSite(client, args[1]);
						break;
					case "info":
					case "i":
					case "stats":
						fetchStats(client, args[1]);
						break;
					case "weekly":
					case "w":
						fetchData(client, selfUuidOrName, args[1], true);
						break;
					case "daily":
					case "d":
						fetchData(client, selfUuidOrName, args[1], false);
						break;
					default:
						client.player.sendSystemMessage(Component.literal("§cCould not register argument: " + args[0]));
						client.player.sendSystemMessage(Component.literal("§cType '/q help' for a list of commands"));
				}
			} else if (args.length == 3) {
				switch (args[0].toLowerCase()) {
					case "weekly":
					case "w":
						fetchData(client, args[2], args[1], true);
						break;
					case "daily":
					case "d":
						fetchData(client, args[2], args[1], false);
						break;
				}
			} else {
				client.player.sendSystemMessage(Component.literal("§cCould not process command"));
				client.player.sendSystemMessage(Component.literal("§cType '/q help' for a list of commands"));
			}
		});
	}

	private void printHelp(Minecraft client) {
		client.player.sendSystemMessage(Component.literal(""));
		client.player.sendSystemMessage(Component.literal("§m----------------------------------------"));
		client.player.sendSystemMessage(Component.literal("§l   HELP§r and §lCOMMANDS"));
		client.player.sendSystemMessage(Component.literal("§m----------------------------------------"));
		client.player.sendSystemMessage(Component.literal("§e - /q daily §7- Your daily quests for game you are playing"));
		client.player.sendSystemMessage(Component.literal("§e - /q weekly §7- Your weekly quests for game you are playing"));
		client.player.sendSystemMessage(Component.literal("§e - /q daily [game] {ign} §7- Your daily quests for specified game"));
		client.player.sendSystemMessage(Component.literal("§e - /q weekly [game] {ign} §7- Your weekly quests for specified game"));
		client.player.sendSystemMessage(Component.literal("§e - /q leaderboard [1-10] §7- View the top 100 quests completed"));
		client.player.sendSystemMessage(Component.literal("§e - /q stats §7- View your general Hypixel stats (- /q stats [ign])"));
		client.player.sendSystemMessage(Component.literal("§e - /q site §7- Link to shmeado.club (- /q site [ign])"));
		client.player.sendSystemMessage(Component.literal("§e - /q games §7- Lists gamemode aliases"));
		client.player.sendSystemMessage(Component.literal("§m----------------------------------------"));
	}

	private void printSite(Minecraft client, String ign) {
		String url = "https://shmeado.club/player/stats/" + ign;

		MutableComponent linkComponent = Component.literal("shmeado.club")
				.withStyle(style -> style
						.withColor(ChatFormatting.AQUA)
						.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal(url)))
				);

		MutableComponent message = Component.literal(" Go to ").append(linkComponent);

		client.player.sendSystemMessage(Component.literal("§m-------------------"));
		client.player.sendSystemMessage(Component.literal(""));
		client.player.sendSystemMessage(message);
		client.player.sendSystemMessage(Component.literal(""));
		client.player.sendSystemMessage(Component.literal("§m-------------------"));
	}

	private void fetchGames(Minecraft client) {
		CompletableFuture.runAsync(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create("https://questviewer-proxy.alexiscanovi78.workers.dev/api/misc/gameAliases/"))
						.header("content-type", "application/json")
						.GET()
						.build();
				HttpResponse<String> result = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
				JsonObject data = JsonParser.parseString(result.body()).getAsJsonObject();

				client.execute(() -> {
					if (client.player == null) return;
					if (data != null && data.has("success") && data.get("success").getAsBoolean()) {
						client.player.sendSystemMessage(Component.literal("§m----------------------------------------"));
						client.player.sendSystemMessage(Component.literal("§l     GAME ALIASES"));
						client.player.sendSystemMessage(Component.literal("§m----------------------------------------"));
						JsonArray array = data.get("data").getAsJsonArray();
						for (JsonElement game : array) {
							JsonObject gameObj = game.getAsJsonObject();
							String name = gameObj.get("name").getAsString();
							StringBuilder aliasList = new StringBuilder();
							JsonArray aliases = gameObj.get("aliases").getAsJsonArray();
							for (int i = 0; i < aliases.size(); i++) {
								if (i > 0) aliasList.append(", ");
								aliasList.append(aliases.get(i).getAsString());
							}
							client.player.sendSystemMessage(Component.literal("§f" + name));
							client.player.sendSystemMessage(Component.literal("§7 - " + aliasList));
						}
						client.player.sendSystemMessage(Component.literal("§m----------------------------------------"));
					} else if (data != null && data.has("cause")) {
						client.player.sendSystemMessage(Component.literal("§cError: " + data.get("cause").getAsString()));
					} else {
						client.player.sendSystemMessage(Component.literal("§cAn error has occurred"));
					}
				});
			} catch (Exception e) {
				QuestViewer.LOGGER.error("Failed to fetch games", e);
			}
		});
	}

	private void fetchLeaderboard(Minecraft client, int requestedPage) {
		final int page = Math.max(1, Math.min(requestedPage, 10));

		CompletableFuture.runAsync(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create("https://plancke.io/hypixel/leaderboards/raw.php?type=player.general.quests"))
						.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
						.GET()
						.build();

				HttpResponse<String> result = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

				JsonObject root = JsonParser.parseString(result.body()).getAsJsonObject();
				String html = root.get("result").getAsString();

				client.execute(() -> {
					if (client.player == null) return;

					client.player.sendSystemMessage(Component.literal("§m----------------------------------------"));

					MutableComponent header = Component.literal("    ");

					if (page > 1) {
						header.append(Component.literal("§b§l<< ")
								.withStyle(style -> style
										.withHoverEvent(new HoverEvent.ShowText(Component.literal("§bClick to view page " + (page - 1))))
										.withClickEvent(new ClickEvent.RunCommand("/q lb " + (page - 1)))
								)
						);
					}

					header.append(Component.literal("§f§lTOP " + (page * 10) + " QUESTS COMPLETED"));

					if (page < 10) {
						header.append(Component.literal(" §b§l>>")
								.withStyle(style -> style
										.withHoverEvent(new HoverEvent.ShowText(Component.literal("§bClick to view page " + (page + 1))))
										.withClickEvent(new ClickEvent.RunCommand("/q lb " + (page + 1)))
								)
						);
					}

					client.player.sendSystemMessage(header);
					client.player.sendSystemMessage(Component.literal("§m----------------------------------------"));

					Pattern rowPattern = Pattern.compile("(?s)<tr>\\s*<td>(\\d+)</td>\\s*<td>(.*?)</td>\\s*<td>([\\d,]+)</td>");
					Matcher matcher = rowPattern.matcher(html);

					int currentRank = 0;
					int startIndex = (page - 1) * 10;
					int endIndex = page * 10;
					boolean foundAny = false;

					while (matcher.find()) {
						currentRank++;

						if (currentRank <= startIndex) {
							continue;
						}
						if (currentRank > endIndex) {
							break;
						}

						foundAny = true;
						String rank = matcher.group(1);
						String rawPlayerCell = matcher.group(2);
						String score = matcher.group(3);

						String username = "";
						Pattern ignPattern = Pattern.compile("/stats/([^\"]+)");
						Matcher ignMatcher = ignPattern.matcher(rawPlayerCell);
						if (ignMatcher.find()) {
							username = ignMatcher.group(1);
						}

						String colorCode = "§f";
						Pattern colorPattern = Pattern.compile("color:\\s*(#[0-9a-fA-F]{6})");
						Matcher colorMatcher = colorPattern.matcher(rawPlayerCell);
						if (colorMatcher.find()) {
							String hex = colorMatcher.group(1).toUpperCase();
							colorCode = hexToMinecraftColor(hex);
						}

						String guildString = "";
						Pattern guildPattern = Pattern.compile("color:\\s*(#[0-9a-fA-F]{6})[^>]*>\\s*(\\[[^\\]]+\\])\\s*</span>\\s*</a>");
						Matcher guildMatcher = guildPattern.matcher(rawPlayerCell);

						if (guildMatcher.find()) {
							String guildHex = guildMatcher.group(1).toUpperCase();
							String guildText = guildMatcher.group(2);
							guildString = " " + hexToMinecraftColor(guildHex) + guildText;
						}

						client.player.sendSystemMessage(Component.literal("§e" + rank + ". " + colorCode + username + guildString + " §7- §e" + score));
					}

					if (!foundAny) {
						client.player.sendSystemMessage(Component.literal("§cCould not parse leaderboard data."));
					}

					client.player.sendSystemMessage(Component.literal("§m----------------------------------------"));
				});
			} catch (Exception e) {
				client.execute(() -> {
					if (client.player != null) {
						client.player.sendSystemMessage(Component.literal("§cError fetching leaderboard."));
					}
				});
				QuestViewer.LOGGER.error("Failed to fetch leaderboard from plancke.io", e);
			}
		});
	}

	private String hexToMinecraftColor(String hex) {
		switch (hex) {
			case "#0000AA": return "§1";
			case "#00AA00": case "#008000": return "§2";
			case "#00AAAA": return "§3";
			case "#AA0000": return "§4";
			case "#AA00AA": return "§5";
			case "#FFAA00": return "§6";
			case "#AAAAAA": return "§7";
			case "#555555": return "§8";
			case "#5555FF": return "§9";
			case "#55FF55": case "#3CE63C": return "§a";
			case "#55FFFF": case "#3CE6E6": return "§b";
			case "#FF5555": return "§c";
			case "#FF55FF": return "§d";
			case "#FFFF55": return "§e";
			case "#FFFFFF": return "§f";
			default: return "§f";
		}
	}

	private void fetchStats(Minecraft client, String name) {
		CompletableFuture.runAsync(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create("https://shmeado.club/player/stats/" + name + "/"))
						.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
						.GET()
						.build();

				HttpResponse<String> result = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
				String html = result.body();

				String cleanText = html.replaceAll("<[^>]+>", " ").replace("&nbsp;", " ");

				client.execute(() -> {
					if (client.player == null) return;

					if (cleanText.contains("Invalid Name/UUID")) {
						client.player.sendSystemMessage(Component.literal("§cError: Player not found on shmeado.club"));
						return;
					}

					client.player.sendSystemMessage(Component.literal("§m----------------------------------------"));
					client.player.sendSystemMessage(Component.literal("§l    " + name.toUpperCase() + "'S GENERAL STATS"));
					client.player.sendSystemMessage(Component.literal("§m----------------------------------------"));

					String level = extractStat(cleanText, "Level:\\s*([\\d,.]+)");
					String quests = extractStat(cleanText, "Quests:\\s*([\\d,]+)");
					String challenges = extractStat(cleanText, "Challenges:\\s*([\\d,]+)");
					String achievementPoints = extractStat(cleanText, "Achievement Points:\\s*([\\d,]+)");
					String karma = extractStat(cleanText, "Karma:\\s*([\\d,]+)");

					client.player.sendSystemMessage(Component.literal("§7 Network Level: §3" + (level != null ? level : "N/A")));
					client.player.sendSystemMessage(Component.literal("§7 Achievement Points: §e" + (achievementPoints != null ? achievementPoints : "N/A")));
					client.player.sendSystemMessage(Component.literal("§7 Quests Completed: §6" + (quests != null ? quests : "N/A")));
					client.player.sendSystemMessage(Component.literal("§7 Challenges Completed: §6" + (challenges != null ? challenges : "N/A")));
					client.player.sendSystemMessage(Component.literal("§7 Karma: §d" + (karma != null ? karma : "N/A")));

					client.player.sendSystemMessage(Component.literal("§m----------------------------------------"));
				});
			} catch (Exception e) {
				client.execute(() -> {
					if (client.player != null) {
						client.player.sendSystemMessage(Component.literal("§cError fetching stats from shmeado.club"));
					}
				});
				QuestViewer.LOGGER.error("Failed to fetch stats from shmeado.club", e);
			}
		});
	}

	private String extractStat(String text, String regex) {
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(text);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

	private void fetchData(Minecraft client, String ign, String game, boolean weekly) {
		CompletableFuture.runAsync(() -> {
			try {
				String url = "https://questviewer-proxy.alexiscanovi78.workers.dev/api/quests/player_simple/" + ign + "?type=" + (weekly ? "weekly" : "daily") + "&game=" + game;
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(url))
						.header("content-type", "application/json")
						.GET()
						.build();

				HttpResponse<String> result = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
				JsonObject data = JsonParser.parseString(result.body()).getAsJsonObject();

				client.execute(() -> {
					if (client.player == null) return;

					if (data != null && data.has("success") && data.get("success").getAsBoolean()) {
						JsonObject questsRoot = data.get("data").getAsJsonObject().get("quests").getAsJsonObject();
						JsonObject typeObject = questsRoot.get(weekly ? "weekly" : "daily").getAsJsonObject();

						if (typeObject.entrySet().isEmpty()) {
							client.player.sendSystemMessage(Component.literal("§c[QuestViewer] No quests found for game: " + game));
							return;
						}

						Map.Entry<String, JsonElement> gameKeyValue = typeObject.entrySet().iterator().next();
						JsonObject questObject = gameKeyValue.getValue().getAsJsonObject();
						String gameName = questObject.get("name").getAsString();
						JsonArray questList = questObject.get("quests").getAsJsonArray();

						// --- LEGACY LOBBY MENU ---
						if (gameName.equalsIgnoreCase("Classic Games") || gameName.equalsIgnoreCase("Legacy")) {
							client.player.sendSystemMessage(Component.literal("§m----------------------------------------"));
							client.player.sendSystemMessage(Component.literal("§eWhich game's " + (weekly ? "weekly" : "daily") + " quests would you like to view?"));
							client.player.sendSystemMessage(Component.literal(""));

							String cmdType = weekly ? "w" : "d";
							sendClickableGame(client, "Arena Brawl", "/q " + cmdType + " arena " + ign);
							sendClickableGame(client, "VampireZ", "/q " + cmdType + " vz " + ign);
							sendClickableGame(client, "Turbo Kart Racers", "/q " + cmdType + " tkr " + ign);
							sendClickableGame(client, "Quakecraft", "/q " + cmdType + " quake " + ign);
							sendClickableGame(client, "The Walls", "/q " + cmdType + " walls " + ign);
							sendClickableGame(client, "Paintball", "/q " + cmdType + " paintball " + ign);

							client.player.sendSystemMessage(Component.literal("§m----------------------------------------"));
							return;
						}
						// -------------------------

						// Handle gamemodes that have no active quests (e.g. Housing, SkyBlock, SMP)
						if (questList.isEmpty()) {
							client.player.sendSystemMessage(Component.literal("§c" + gameName + " doesn't have any quests!"));
							return;
						}

						client.player.sendSystemMessage(Component.literal("§m-------------------"));
						client.player.sendSystemMessage(Component.literal("\n §l" + gameName + "\n " + (weekly ? "Weekly" : "Daily") + " Quests\n"));

						for (JsonElement quest : questList) {
							JsonObject questObj = quest.getAsJsonObject();
							JsonObject statusObject = questObj.get("status").getAsJsonObject();
							client.player.sendSystemMessage(Component.literal("§m-------------------"));

							JsonArray objectives = statusObject.get("objectives").getAsJsonArray();
							for (JsonElement objElem : objectives) {
								JsonObject obj = objElem.getAsJsonObject();
								String desc = obj.get("description").getAsString();
								int progress = obj.get("progress").getAsInt();
								int goal = obj.get("goal").getAsInt();

								String color;
								if (progress >= goal) {
									color = "§a";
								} else if (progress == 0) {
									color = "§c";
								} else {
									color = "§e";
								}

								client.player.sendSystemMessage(Component.literal("§f" + desc));
								client.player.sendSystemMessage(Component.literal(color + progress + "/" + goal));
							}
						}
						client.player.sendSystemMessage(Component.literal("§m-------------------"));

					} else if (data != null && data.has("cause")) {
						client.player.sendSystemMessage(Component.literal("§cError: " + data.get("cause").getAsString()));
					} else {
						client.player.sendSystemMessage(Component.literal("§cError: Could not display quests"));
					}
				});
			} catch (Exception e) {
				client.execute(() -> {
					if (client.player != null) {
						client.player.sendSystemMessage(Component.literal("§cError: Could not display quests"));
					}
				});
				QuestViewer.LOGGER.error("Failed to fetch quests from Cloudflare Proxy", e);
			}
		});
	}

	private void sendClickableGame(Minecraft client, String name, String command) {
		MutableComponent comp = Component.literal(" §7- §b§l" + name)
				.withStyle(style -> style
						.withClickEvent(new ClickEvent.RunCommand(command))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to view " + name + " quests")))
				);
		client.player.sendSystemMessage(comp);
	}
}