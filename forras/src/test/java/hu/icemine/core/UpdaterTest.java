package hu.icemine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.icemine.core.board.TabListAccess;
import org.junit.jupiter.api.Test;

class UpdaterTest {

    @Test
    void versionsCompareNumerically() {
        assertEquals(1, Updater.compare("3.14.2", "3.14.3"));
        assertEquals(1, Updater.compare("3.14.9", "3.14.10"));
        assertEquals(-1, Updater.compare("3.15.0", "3.14.10"));
        assertEquals(0, Updater.compare("3.14.3", "3.14.3"));
        assertEquals(1, Updater.compare("3.14", "3.14.1"));
        assertEquals(0, Updater.compare("3.14.3", " 3.14.3\n"));
    }

    @Test
    void tabSortKeysFitAndOrderByRank() {
        String owner = TabListAccess.sortKey(14, "Peti");
        String player = TabListAccess.sortKey(0, "Anna");
        assertTrue(owner.compareTo(player) < 0, "a magasabb rang feljebb van");
        assertTrue(TabListAccess.sortKey(3, "NagyonHosszuNev16").length() <= 16);
    }
}
