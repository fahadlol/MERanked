package com.meranked.util;

import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Base64;

/**
 * Full-fidelity ItemStack[] serialization using Paper's native byte serializer.
 * Preserves NBT, shulker contents, armor trims, enchants, and custom data.
 */
public final class ItemSerializer {

    private ItemSerializer() {}

    public static String toBase64(ItemStack[] items) {
        if (items == null) return "";
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             DataOutputStream data = new DataOutputStream(out)) {
            data.writeInt(items.length);
            for (ItemStack item : items) {
                if (item == null) {
                    data.writeInt(0);
                } else {
                    byte[] bytes = item.serializeAsBytes();
                    data.writeInt(bytes.length);
                    data.write(bytes);
                }
            }
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception ex) {
            return "";
        }
    }

    public static ItemStack[] fromBase64(String encoded, int fallbackSize) {
        if (encoded == null || encoded.isEmpty()) return new ItemStack[fallbackSize];
        try (ByteArrayInputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
             DataInputStream data = new DataInputStream(in)) {
            int length = data.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                int size = data.readInt();
                if (size == 0) {
                    items[i] = null;
                } else {
                    byte[] bytes = new byte[size];
                    data.readFully(bytes);
                    items[i] = ItemStack.deserializeBytes(bytes);
                }
            }
            return items;
        } catch (Exception ex) {
            return new ItemStack[fallbackSize];
        }
    }
}
