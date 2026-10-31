package hu.icemine.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class Values {

    private final String file;
    private final Function<String, Object> lookup;
    private final List<String> problems = new ArrayList<>();

    public Values(String file, Function<String, Object> lookup) {
        this.file = file;
        this.lookup = lookup;
    }

    public List<String> problems() {
        return Collections.unmodifiableList(problems);
    }

    private void problem(String path, String message) {
        problems.add(file + " -> " + path + ": " + message);
    }

    public int integer(String path, int fallback, int min, int max) {
        Object raw = lookup.apply(path);
        if (raw == null) {
            return fallback;
        }
        long value;
        if (raw instanceof Number) {
            value = ((Number) raw).longValue();
        } else {
            try {
                value = Long.parseLong(raw.toString().trim());
            } catch (NumberFormatException exception) {
                problem(path, "nem szám (\"" + raw + "\"), az alapértéket használom: " + fallback);
                return fallback;
            }
        }
        if (value < min || value > max) {
            int clamped = (int) Math.max(min, Math.min(max, value));
            problem(path, "a megadott érték (" + value + ") kívül esik ezen: " + min + ".." + max + ", ezt használom helyette: " + clamped);
            return clamped;
        }
        return (int) value;
    }

    public long number(String path, long fallback, long min, long max) {
        Object raw = lookup.apply(path);
        if (raw == null) {
            return fallback;
        }
        long value;
        if (raw instanceof Number) {
            value = ((Number) raw).longValue();
        } else {
            try {
                value = Long.parseLong(raw.toString().trim());
            } catch (NumberFormatException exception) {
                problem(path, "nem szám (\"" + raw + "\"), az alapértéket használom: " + fallback);
                return fallback;
            }
        }
        if (value < min || value > max) {
            long clamped = Math.max(min, Math.min(max, value));
            problem(path, "a megadott érték (" + value + ") kívül esik ezen: " + min + ".." + max + ", ezt használom helyette: " + clamped);
            return clamped;
        }
        return value;
    }

    public double decimal(String path, double fallback, double min, double max) {
        Object raw = lookup.apply(path);
        if (raw == null) {
            return fallback;
        }
        double value;
        if (raw instanceof Number) {
            value = ((Number) raw).doubleValue();
        } else {
            try {
                value = Double.parseDouble(raw.toString().trim().replace(',', '.'));
            } catch (NumberFormatException exception) {
                problem(path, "nem szám (\"" + raw + "\"), az alapértéket használom: " + fallback);
                return fallback;
            }
        }
        if (Double.isNaN(value) || value < min || value > max) {
            double clamped = Double.isNaN(value) ? fallback : Math.max(min, Math.min(max, value));
            problem(path, "a megadott érték (" + value + ") kívül esik ezen: " + min + ".." + max + ", ezt használom helyette: " + clamped);
            return clamped;
        }
        return value;
    }

    public boolean bool(String path, boolean fallback) {
        Object raw = lookup.apply(path);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        String text = raw.toString().trim().toLowerCase(Locale.ROOT);
        switch (text) {
            case "true":
            case "igen":
            case "yes":
            case "on":
                return true;
            case "false":
            case "nem":
            case "no":
            case "off":
                return false;
            default:
                problem(path, "nem igaz/hamis érték (\"" + raw + "\"), ezt használom: " + fallback);
                return fallback;
        }
    }

    public String text(String path, String fallback) {
        Object raw = lookup.apply(path);
        return raw == null ? fallback : raw.toString();
    }

    public String choice(String path, String fallback, String... allowed) {
        String value = text(path, fallback).toLowerCase(Locale.ROOT);
        for (String option : allowed) {
            if (option.equals(value)) {
                return value;
            }
        }
        problem(path, "ismeretlen érték (\"" + value + "\"), lehetséges: " + String.join(", ", allowed) + ", ezt használom: " + fallback);
        return fallback;
    }
}
