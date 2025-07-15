package com.rinko1231.cutewords;


import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.slf4j.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Mod(CuteWords.MODID)
public class CuteWords {
    public static final String MODID = "cutewords";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Path sensitiveFilePath = Paths.get("config/cuteWords.toml");
    private final Path caseInsensitiveFilePath = Paths.get("config/cuteWordsCaseInsensitive.toml");

    // 将replacementRules改为存储Pattern
    private Map<Pattern, String> replacementRules = new HashMap<>();
    private Map<Pattern, String> caseInsensitiveRules = new HashMap<>();

    public CuteWords(IEventBus modEventBus) {
        // 注册mod事件
        modEventBus.addListener(this::setup);
        NeoForge.EVENT_BUS.register(this);

    }

    private void setup(final FMLCommonSetupEvent event) {
        createDefaultConfigFileIfNotExists(sensitiveFilePath, Arrays.asList("\"114514\"===\"1919810\"")); // 创建默认配置文件
        createDefaultConfigFileIfNotExists(caseInsensitiveFilePath, Arrays.asList("\" usa\"===\" USA\"", "\"Ciallo\"===\"Ciallo～(∠・ω< )⌒☆\"")); // 创建大小写不敏感文件

        loadReplacementRules();             // 加载大小写敏感规则
        loadCaseInsensitiveRules();         // 加载大小写不敏感规则
    }
    // 创建默认配置文件
    private void createDefaultConfigFileIfNotExists(Path filePath, List<String> defaultLines) {
        try {
            if (!Files.exists(filePath)) {
                Files.createDirectories(filePath.getParent()); // 确保父目录存在
                Files.write(filePath, defaultLines, StandardOpenOption.CREATE);
                LOGGER.info("Created default config file: {}", filePath);
            }
        } catch (IOException e) {
            LOGGER.error("Error creating config file: {}", filePath, e);
        }
    }


    // 加载大小写敏感规则
    private void loadReplacementRules() {
        try {
            List<String> lines = Files.readAllLines(sensitiveFilePath);
            replacementRules = parseRulesWithWildcard(lines);
        } catch (IOException e) {
            LOGGER.error("Error loading sensitive CuteWords config file", e);
        }
    }

    // 加载大小写不敏感规则
    private void loadCaseInsensitiveRules() {
        try {
            List<String> lines = Files.readAllLines(caseInsensitiveFilePath);
            caseInsensitiveRules = parseRulesWithWildcard(lines).entrySet().stream()
                    .collect(Collectors.toMap(
                            entry -> Pattern.compile(entry.getKey().pattern(), Pattern.CASE_INSENSITIVE), // 忽略大小写
                            Map.Entry::getValue
                    ));
        } catch (IOException e) {
            LOGGER.error("Error loading case-insensitive CuteWords config file", e);
        }
    }

    private Map<Pattern, String> parseRulesWithWildcard(List<String> lines) {
        Map<Pattern, String> rules = new HashMap<>();

        for (String line : lines) {
            if (line.contains("===")) {
                String[] parts = line.split("===", 2);
                if (parts.length == 2) {
                    String key = extractQuotedString(parts[0].trim());
                    String value = extractQuotedString(parts[1].trim());
                    if (key != null && value != null) {
                        RegexWithGroupCount regexInfo = convertWildcardToRegex(key);

                        //  \* 和 \? 转义
                        StringBuilder replacedValue = new StringBuilder();
                        int groupIndex = 1;
                        boolean escaping = false;

                        for (int i = 0; i < value.length(); i++) {
                            char c = value.charAt(i);
                            if (escaping) {
                                // 任何 \后面的字符都原样输出
                                replacedValue.append(c);
                                escaping = false;
                            } else if (c == '\\') {
                                escaping = true; // 进入转义状态
                            } else if ((c == '*' || c == '?') && groupIndex <= regexInfo.groupCount) {
                                replacedValue.append("$").append(groupIndex++);
                            } else {
                                replacedValue.append(c);
                            }
                        }

                        Pattern keyPattern = Pattern.compile(regexInfo.regex);
                        rules.put(keyPattern, replacedValue.toString());
                    }
                }
            }
        }

        return rules;
    }

    // 辅助类：记录正则表达式和捕获组数量
        private record RegexWithGroupCount(String regex, int groupCount) {
    }

    private RegexWithGroupCount convertWildcardToRegex(String wildcard) {
        StringBuilder regex = new StringBuilder();
        boolean escaping = false;
        int groupCount = 0;

        for (int i = 0; i < wildcard.length(); i++) {
            char currentChar = wildcard.charAt(i);
            if (escaping) {
                regex.append(Pattern.quote(String.valueOf(currentChar)));
                escaping = false;
            } else {
                if (currentChar == '\\') {
                    escaping = true;
                } else if (currentChar == '*') {
                    regex.append("(.*?)");
                    groupCount++;
                } else if (currentChar == '?') {
                    regex.append("(.)"); // 必须正好一个字符
                    groupCount++;
                } else {
                    regex.append(Pattern.quote(String.valueOf(currentChar)));
                }
            }
        }

        return new RegexWithGroupCount(regex.toString(), groupCount);
    }

    // 提取双引号中的内容
    private String extractQuotedString(String str) {
        if (str.startsWith("\"") && str.endsWith("\"")) {
            return str.substring(1, str.length() - 1); // 去除双引号
        }
        return null;
    }


    public void loadConfiguration() {
        loadReplacementRules();
        loadCaseInsensitiveRules();
    }

    @SubscribeEvent
    public void onReload(AddReloadListenerEvent event) {
        // 调用主类中的loadConfiguration()方法
        loadConfiguration();
    }

    @SubscribeEvent
    public void onPlayerChat(ServerChatEvent event) {
        String message = event.getMessage().getString();
        // 处理大小写敏感的替换
        for (Map.Entry<Pattern, String> entry : replacementRules.entrySet()) {
            Matcher matcher = entry.getKey().matcher(message);
            if (matcher.find()) {
                message = matcher.replaceAll(entry.getValue());
            }
        }

        // 处理大小写不敏感的替换
        for (Map.Entry<Pattern, String> entry : caseInsensitiveRules.entrySet()) {
            Matcher matcher = entry.getKey().matcher(message);
            if (matcher.find()) {
                message = matcher.replaceAll(entry.getValue());
            }
        }

        event.setMessage(Component.literal(message)); // 更新消息
     }
}

