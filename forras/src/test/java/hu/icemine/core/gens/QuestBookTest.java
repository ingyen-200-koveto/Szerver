package hu.icemine.core.gens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestBookTest {

    private final UUID player = UUID.fromString("8b2f6f4e-2a1d-4d0c-9a51-1f6f0b7c2e11");

    private QuestBook book(int perDay) {
        List<QuestBook.Quest> quests = new ArrayList<>();
        quests.add(new QuestBook.Quest("elad1", "sell", 100, 50, "Adj el 100 tárgyat"));
        quests.add(new QuestBook.Quest("gyujt1", "collect", 200, 60, "Gyűjts 200 tárgyat"));
        quests.add(new QuestBook.Quest("fejleszt1", "upgrade", 3, 80, "Fejlessz 3 generátort"));
        quests.add(new QuestBook.Quest("lerak1", "place", 5, 40, "Rakj le 5 generátort"));
        quests.add(new QuestBook.Quest("elad2", "sell", 500, 150, "Adj el 500 tárgyat"));
        QuestBook book = new QuestBook();
        book.configure(quests, perDay);
        return book;
    }

    private static List<String> ids(List<QuestBook.Quest> quests) {
        List<String> out = new ArrayList<>();
        for (QuestBook.Quest quest : quests) {
            out.add(quest.id);
        }
        return out;
    }

    @Test
    void dailyQuestsAreStableForTheDay() {
        QuestBook book = book(3);
        assertEquals(ids(book.today(player, "2026-10-18")), ids(book.today(player, "2026-10-18")));
        assertEquals(3, book.today(player, "2026-10-18").size());
    }

    @Test
    void perDayIsLimitedByPool() {
        assertEquals(5, book(10).today(player, "2026-10-18").size());
        assertEquals(1, book(0).today(player, "2026-10-18").size());
    }

    @Test
    void progressCompletesQuestOnce() {
        QuestBook book = book(5);
        GensData.Player data = new GensData.Player(player, 0, 1);
        assertTrue(book.add(data, "2026-10-18", "place", 3).isEmpty());
        List<QuestBook.Quest> done = book.add(data, "2026-10-18", "place", 4);
        assertEquals(1, done.size());
        assertEquals("lerak1", done.get(0).id);
        assertTrue(book.add(data, "2026-10-18", "place", 10).isEmpty());
        assertEquals(5, book.progress(data, done.get(0)));
        assertTrue(data.dirty);
    }

    @Test
    void sameTypeFeedsEveryMatchingQuest() {
        QuestBook book = book(5);
        GensData.Player data = new GensData.Player(player, 0, 1);
        List<QuestBook.Quest> done = book.add(data, "2026-10-18", "sell", 120);
        assertEquals(1, done.size());
        assertEquals("elad1", done.get(0).id);
        assertEquals(120L, data.progress.get("elad2"));
    }

    @Test
    void newDayResetsProgress() {
        QuestBook book = book(5);
        GensData.Player data = new GensData.Player(player, 0, 1);
        book.add(data, "2026-10-18", "collect", 150);
        book.roll(data, "2026-10-19");
        assertTrue(data.progress.isEmpty());
        assertTrue(data.claimed.isEmpty());
        assertEquals("2026-10-19", data.questDay);
    }

    @Test
    void zeroOrNegativeAmountDoesNothing() {
        QuestBook book = book(5);
        GensData.Player data = new GensData.Player(player, 0, 1);
        assertTrue(book.add(data, "2026-10-18", "sell", 0).isEmpty());
        assertTrue(book.add(data, "2026-10-18", "sell", -10).isEmpty());
        assertTrue(data.progress.isEmpty());
    }

    @Test
    void questStateSurvivesEncoding() {
        GensData.Player data = new GensData.Player(player, 0, 1);
        data.questDay = "2026-10-18";
        data.progress.put("gyujt1", 42L);
        data.claimed.add("elad1");
        GensData.Player copy = new GensData.Player(player, 0, 1);
        copy.decodeQuests(data.encodeQuests());
        assertEquals("2026-10-18", copy.questDay);
        assertEquals(42L, copy.progress.get("gyujt1"));
        assertTrue(copy.claimed.contains("elad1"));
        assertFalse(copy.progress.containsKey("elad1"));
    }

    @Test
    void brokenQuestTextIsIgnored() {
        GensData.Player data = new GensData.Player(player, 0, 1);
        data.decodeQuests("day=2026-10-18;rossz;x=abc;=5;gyujt1=7");
        assertEquals("2026-10-18", data.questDay);
        assertEquals(1, data.progress.size());
        assertEquals(7L, data.progress.get("gyujt1"));
        data.decodeQuests(null);
        assertEquals("", data.questDay);
    }

    @Test
    void trustedListSurvivesEncoding() {
        GensData.Player data = new GensData.Player(player, 0, 1);
        UUID friend = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        data.trusted.add(friend);
        data.trusted.add(other);
        GensData.Player copy = new GensData.Player(player, 0, 1);
        copy.decodeTrusted(data.encodeTrusted() + ",nem-uuid");
        assertEquals(2, copy.trusted.size());
        assertTrue(copy.trusted.contains(friend));
        assertTrue(copy.trusted.contains(other));
    }
}
