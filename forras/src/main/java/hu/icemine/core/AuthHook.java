package hu.icemine.core;

import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

public final class AuthHook {

    private final Object api;
    private final Method check;

    AuthHook(Logger logger) {
        Object foundApi = null;
        Method foundCheck = null;
        try {
            Class<?> type = Class.forName("fr.xephi.authme.api.v3.AuthMeApi");
            foundApi = type.getMethod("getInstance").invoke(null);
            foundCheck = type.getMethod("isAuthenticated", Player.class);
        } catch (Throwable ignored) {
            foundApi = null;
            foundCheck = null;
        }
        api = foundApi;
        check = foundCheck;
        if (api != null && check != null) {
            logger.info("AuthMe megtalálva: a menük és a szobrok csak bejelentkezés után működnek.");
        }
    }

    public boolean isLoggedIn(Player player) {
        if (api == null || check == null) {
            return true;
        }
        try {
            Object result = check.invoke(api, player);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable ignored) {
            return true;
        }
    }
}
