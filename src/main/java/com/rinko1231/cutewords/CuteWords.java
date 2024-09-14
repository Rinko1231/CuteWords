package com.rinko1231.cutewords;


import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public CuteWords() {
        // 注册mod事件
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);

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
                LOGGER.info("Created default config file: " + filePath);
            }
        } catch (IOException e) {
            LOGGER.error("Error creating config file: " + filePath, e);
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

    // 解析规则，支持通配符并处理转义符
    private Map<Pattern, String> parseRulesWithWildcard(List<String> lines) {
        Map<Pattern, String> rules = new HashMap<>();

        for (String line : lines) {
            if (line.contains("===")) {
                String[] parts = line.split("===", 2);
                if (parts.length == 2) {
                    String key = extractQuotedString(parts[0].trim());
                    String value = extractQuotedString(parts[1].trim());
                    if (key != null && value != null) {
                        // 将通配符转换为正则表达式
                        Pattern keyPattern = Pattern.compile(convertWildcardToRegex(key));
                        rules.put(keyPattern, value);
                    }
                }
            }
        }

        return rules;
    }

    // 将带有通配符的字符串转换为正则表达式
    private String convertWildcardToRegex(String wildcard) {
        StringBuilder regex = new StringBuilder();
        boolean escaping = false;

        for (int i = 0; i < wildcard.length(); i++) {
            char currentChar = wildcard.charAt(i);

            if (escaping) {
                // 处理转义符
                regex.append(Pattern.quote(String.valueOf(currentChar)));
                escaping = false;
            } else {
                if (currentChar == '\\') {
                    escaping = true; // 下一个字符需要转义
                } else if (currentChar == '*') {
                    regex.append(".*"); // * 转换为正则表达式的任意字符
                } else if (currentChar == '?') {
                    regex.append("."); // ? 转换为正则表达式的单个字符
                } else {
                    regex.append(Pattern.quote(String.valueOf(currentChar))); // 普通字符
                }
            }
        }

        return regex.toString();
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

