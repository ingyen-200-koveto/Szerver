package hu.icemine.core;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;
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

    public boolean present() {
        return api != null && check != null;
    }

    private Object call(String name, Class<?>[] types, Object... args) throws Exception {
        return api.getClass().getMethod(name, types).invoke(api, args);
    }

    public boolean isRegistered(String name) {
        try {
            return Boolean.TRUE.equals(call("isRegistered", new Class<?>[]{String.class}, name));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean checkPassword(String name, String password) {
        try {
            return Boolean.TRUE.equals(call("checkPassword", new Class<?>[]{String.class, String.class}, name, password));
        } catch (Exception e) {
            return false;
        }
    }

    /** Mikor regisztrált a szerveren (AuthMe), ha tudható. */
    public Instant registeredAt(String name) {
        try {
            Object info = call("getPlayerInfo", new Class<?>[]{String.class}, name);
            if (info instanceof Optional && ((Optional<?>) info).isPresent()) {
                Object player = ((Optional<?>) info).get();
                Method date = Class.forName("fr.xephi.authme.api.v3.AuthMePlayer", true, api.getClass().getClassLoader())
                        .getMethod("getRegistrationDate");
                Object value = date.invoke(player);
                return value instanceof Instant ? (Instant) value : null;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Beléptetés a megadott jelszóval: ha még nincs szerveres regisztráció, létrehozza, ha van, a jelszavát erre cseréli. */
    public boolean loginWith(Player player, String password) {
        if (!present()) {
            return false;
        }
        try {
            if (isRegistered(player.getName())) {
                if (!checkPassword(player.getName(), password)) {
                    call("changePassword", new Class<?>[]{String.class, String.class}, player.getName(), password);
                }
                call("forceLogin", new Class<?>[]{Player.class}, player);
            } else {
                if (!Boolean.TRUE.equals(call("registerPlayer", new Class<?>[]{String.class, String.class}, player.getName(), password))) {
                    return false;
                }
                call("forceLogin", new Class<?>[]{Player.class}, player);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean forceLogin(Player player) {
        try {
            call("forceLogin", new Class<?>[]{Player.class}, player);
            return true;
        } catch (Exception e) {
            return false;
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
