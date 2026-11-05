package com.boomerzap.CSCase;

import java.util.BitSet;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;

/**
 * A case's display name. It is an ordinary text component, except that {@code "color": "rainbow"} is
 * understood: a literal {@code text} with that colour is expanded into one coloured letter per character
 * (so it works in item names, chat and tooltips), and the case screen additionally animates those letters.
 *
 * <p>Rainbow applies to the object's own {@code text}. Entries in its {@code extra} list keep their own
 * colours instead of inheriting the rainbow. Rainbow on a non-literal component (e.g. {@code translate}) is ignored.
 */
public final class CaseName {
    public static final String RAINBOW = "rainbow";

    private static final float SATURATION = 0.75f;
    private static final float BRIGHTNESS = 1.0f;

    private final JsonElement raw;
    private final Component component;
    private final BitSet rainbowChars;

    private CaseName(JsonElement raw, Component component, BitSet rainbowChars) {
        this.raw = raw;
        this.component = component;
        this.rainbowChars = rainbowChars;
    }

    /** The name with a static rainbow gradient baked in. */
    public Component component() {
        return component;
    }

    public boolean hasRainbow() {
        return !rainbowChars.isEmpty();
    }

    /** The name with the rainbow letters shifted by {@code phase} (0..1, wraps). Other text is unchanged. */
    public Component animated(float phase) {
        if (!hasRainbow()) return component;
        MutableComponent out = Component.empty();
        int[] pos = {0};
        component.visit((style, text) -> {
            text.codePoints().forEach(cp -> {
                String ch = new String(Character.toChars(cp));
                Style s = rainbowChars.get(pos[0])
                        ? style.withColor(Mth.hsvToRgb(Mth.frac(pos[0] * 0.07f - phase), SATURATION, BRIGHTNESS))
                        : style;
                out.append(Component.literal(ch).withStyle(s));
                pos[0] += Character.charCount(cp);
            });
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    // ---------------------------------------------------------------- codec

    public static final Codec<CaseName> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<CaseName, T>> decode(DynamicOps<T> ops, T input) {
            JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
            return CaseName.fromJson(json).map(name -> Pair.of(name, ops.empty()));
        }

        @Override
        public <T> DataResult<T> encode(CaseName name, DynamicOps<T> ops, T prefix) {
            // Keep the original JSON so a client rebuilds the identical name (and rainbow positions).
            return ops.mergeToPrimitive(prefix, JsonOps.INSTANCE.convertTo(ops, name.raw));
        }
    };

    public static DataResult<CaseName> fromJson(JsonElement json) {
        State state = new State();
        JsonElement expanded = expand(json, state);
        return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, expanded)
                .map(component -> new CaseName(json, component, state.rainbow));
    }

    private static final class State {
        /** Position in the flattened plain text; used to remember which characters are rainbow. */
        int pos;
        final BitSet rainbow = new BitSet();
    }

    private static JsonElement expand(JsonElement el, State state) {
        if (el.isJsonPrimitive()) {
            if (el.getAsJsonPrimitive().isString()) state.pos += el.getAsString().length();
            return el;
        }
        if (el.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement child : el.getAsJsonArray()) out.add(expand(child, state));
            return out;
        }
        if (!el.isJsonObject()) return el;

        JsonObject src = el.getAsJsonObject();
        boolean rainbow = src.has("color") && src.get("color").isJsonPrimitive()
                && RAINBOW.equals(src.get("color").getAsString());
        boolean literal = src.has("text") && src.get("text").isJsonPrimitive();

        JsonObject out = new JsonObject();
        for (var entry : src.entrySet()) {
            String key = entry.getKey();
            if (key.equals("extra")) continue;
            if (key.equals("color") && rainbow) continue;
            if (key.equals("text") && rainbow && literal) continue;
            out.add(key, entry.getValue());
        }

        JsonArray extra = new JsonArray();
        if (literal && rainbow) {
            String text = src.get("text").getAsString();
            out.addProperty("text", "");
            int total = (int) text.codePoints().count();
            float spread = Math.max(total, 8);
            int k = 0;
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                int len = Character.charCount(cp);
                JsonObject letter = new JsonObject();
                letter.addProperty("text", new String(Character.toChars(cp)));
                letter.addProperty("color", String.format("#%06X",
                        Mth.hsvToRgb(Mth.frac(k / spread), SATURATION, BRIGHTNESS) & 0xFFFFFF));
                extra.add(letter);
                state.rainbow.set(state.pos, state.pos + len);
                state.pos += len;
                i += len;
                k++;
            }
        } else if (literal) {
            state.pos += src.get("text").getAsString().length();
        }

        if (src.has("extra") && src.get("extra").isJsonArray()) {
            for (JsonElement child : src.getAsJsonArray("extra")) extra.add(expand(child, state));
        }
        if (!extra.isEmpty()) out.add("extra", extra);
        return out;
    }
}
