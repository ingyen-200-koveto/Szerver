package hu.icemine.kingdoms;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class Kingdom {

    enum Role {
        KING("Király", 3),
        KNIGHT("Lovag", 2),
        MEMBER("Tag", 1);

        final String title;
        final int power;

        Role(String title, int power) {
            this.title = title;
            this.power = power;
        }
    }

    String name;
    UUID leader;
    long created;
    final Map<UUID, Role> members = new LinkedHashMap<>();
    final Set<String> claims = new HashSet<>();
    double[] home;

    Kingdom(String name, UUID leader, long created) {
        this.name = name;
        this.leader = leader;
        this.created = created;
    }

    Role role(UUID id) {
        return members.get(id);
    }

    boolean canManage(UUID id) {
        Role role = members.get(id);
        return role != null && role.power >= Role.KNIGHT.power;
    }

    static String chunk(int cx, int cz) {
        return cx + ";" + cz;
    }
}
