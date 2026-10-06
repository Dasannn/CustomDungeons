package dev.dasan.customdungeons.config;
import java.util.Map;
public record ValidationError(String path, String messageKey, Map<String,String> args) {
    public ValidationError { args = Map.copyOf(args); }
}
