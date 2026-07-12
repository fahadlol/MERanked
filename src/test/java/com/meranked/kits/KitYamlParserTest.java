package com.meranked.kits;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KitYamlParserTest {

    @Test
    void emptyListReturnsSizedArray() {
        ItemStack[] arr = KitYamlParser.parse(List.of(), 41);
        assertEquals(41, arr.length);
        assertNull(arr[0]);
    }

    @Test
    void nonListReturnsSizedEmptyArray() {
        ItemStack[] arr = KitYamlParser.parse("not-a-list", 10);
        assertEquals(10, arr.length);
        assertNull(arr[0]);
    }
}
