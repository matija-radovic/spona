package io.github.matijaradovic.spona.reaction;

import java.util.List;

/** The closed set of emojis a reaction may carry, in the order ties are broken. */
public final class ReactionPalette {

    public static final List<String> EMOJIS = List.of(
            "👍", "❤️", "😂", "🎉", "😮",
            "😢", "🙏", "👏", "🔥", "💡",
            "😍", "🤔", "😎", "🚀", "💯",
            "🙌", "😅", "🤝", "👀", "✅");

    private ReactionPalette() {
    }

    public static boolean contains(String emoji) {
        return EMOJIS.contains(emoji);
    }

    public static int indexOf(String emoji) {
        int index = EMOJIS.indexOf(emoji);
        return index < 0 ? EMOJIS.size() : index;
    }
}
