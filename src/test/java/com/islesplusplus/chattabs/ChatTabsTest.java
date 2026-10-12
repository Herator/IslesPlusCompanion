package com.islesplusplus.chattabs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatTabsTest {
    @Test
    void testMatches() {
        assertTrue(ChatTabs.matches("", "anything"));
        assertTrue(ChatTabs.matches("guild", "GUILD Bob » hi"));
        assertFalse(ChatTabs.matches("guild", "Bob » hi"));
        assertTrue(ChatTabs.matches("guild, party", "PARTY Bob » hi"));
        assertFalse(ChatTabs.matches("-»", "Bob » hi"));
        assertTrue(ChatTabs.matches("-»", "You found a chest"));
        assertFalse(ChatTabs.matches("», -meteor", "(!!!) A Mana Meteor » somewhere"));
        assertTrue(ChatTabs.matches(" , - ", "empty terms are ignored"));
    }

    @Test
    void testSplitJoin() {
        assertArrayEquals(new String[]{"wts, wtb", "guild"}, ChatTabs.split("wts, -guild,wtb, ,-"));
        assertArrayEquals(new String[]{"", ""}, ChatTabs.split(""));
        assertEquals("wts, wtb, -guild", ChatTabs.join(" wts,wtb ,", "guild"));
        assertEquals("", ChatTabs.join("", " , "));
    }
}
