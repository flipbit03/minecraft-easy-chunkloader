/*
 * Derived from MiniMessage (adventure-text-minimessage 4.26.1), part of adventure,
 * https://github.com/KyoriPowered/adventure, licensed under the MIT License:
 *
 * Copyright (c) 2017-2025 KyoriPowered
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package dev.cadu.chunkloader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;

/**
 * Renders MiniMessage-formatted config strings to legacy {@code §}-coded text, so the
 * plugin needs no Adventure on the server (plain Spigot doesn't ship it) while every
 * config.yml written for the old Paper build keeps rendering the same.
 *
 * <p>The tokenizer and tree building are ported from MiniMessage itself, so escapes,
 * quoting, unclosed/mismatched tags and unknown tags (left as literal text, e.g. the
 * {@code /<command>} in {@code name-usage}) behave exactly as before. Supported tags:
 * named / hex colours ({@code <red>}, {@code <#41c7c7>}, {@code <color:..>}), decorations
 * ({@code <bold>}, {@code <!italic>}, {@code <b:false>}, ...), {@code <reset>},
 * {@code <gradient:..>}, {@code <newline>}/{@code <br>} and unparsed placeholders.
 * Any other tag (hover, click, rainbow, font, ...) is not supported and stays literal text.
 */
final class LegacyMiniMessage {

    private static final char SECTION = '\u00a7';
    private static final Pattern TAG_NAME = Pattern.compile("[!?#]?[a-z0-9_-]*");

    /** MiniMessage / vanilla named colours, in legacy-code order (index == code digit). */
    private static final String[] COLOR_NAMES = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};
    private static final int[] COLOR_VALUES = {
            0x000000, 0x0000aa, 0x00aa00, 0x00aaaa, 0xaa0000, 0xaa00aa, 0xffaa00, 0xaaaaaa,
            0x555555, 0x5555ff, 0x55ff55, 0x55ffff, 0xff5555, 0xff55ff, 0xffff55, 0xffffff};

    /** Decorations in {@link Style#decorations} order, with their names and legacy codes. */
    private static final String[][] DECORATION_NAMES = {
            {"obfuscated", "obf"},
            {"bold", "b"},
            {"strikethrough", "st"},
            {"underlined", "u"},
            {"italic", "em", "i"}};
    private static final char[] DECORATION_CODES = {'k', 'l', 'm', 'n', 'o'};

    private LegacyMiniMessage() {
    }

    /**
     * Parses {@code input} and returns it as legacy text. Each placeholder {@code <key>} is
     * replaced by its value verbatim (never parsed as tags), like MiniMessage's
     * {@code Placeholder.unparsed}.
     */
    static String render(String input, Map<String, String> placeholders) {
        Element root = buildTree(input, tokenize(input), placeholders);
        List<StyledChar> chars = new ArrayList<>();
        flatten(root, Style.EMPTY, null, new ArrayList<>(), chars);
        return toLegacy(chars);
    }

    // ---- tokenizer (port of MiniMessage's TokenParser) ------------------------------

    private enum TokenType { TEXT, OPEN_TAG, CLOSE_TAG, OPEN_CLOSE_TAG }

    /** A token; for tags, {@code parts} holds [start, end) of the name and each argument. */
    private record Token(TokenType type, int start, int end, List<int[]> parts) {
    }

    private enum FirstPassState { NORMAL, TAG, STRING }

    private static List<Token> tokenize(String message) {
        List<Token> tokens = new ArrayList<>();
        FirstPassState state = FirstPassState.NORMAL;
        boolean escaped = false;
        int currentTokenEnd = 0;
        int marker = -1;
        char currentStringChar = 0;

        int length = message.length();
        for (int i = 0; i < length; i++) {
            int codePoint = message.codePointAt(i);
            if (!Character.isBmpCodePoint(codePoint)) {
                i++;
            }
            if (!escaped) {
                if (codePoint == '\\' && i + 1 < length) {
                    int nextCodePoint = message.codePointAt(i + 1);
                    switch (state) {
                        case NORMAL -> escaped = nextCodePoint == '<' || nextCodePoint == '\\';
                        case STRING -> escaped = currentStringChar == nextCodePoint || nextCodePoint == '\\';
                        case TAG -> {
                            if (nextCodePoint == '<') {
                                escaped = true;
                                state = FirstPassState.NORMAL;
                            }
                        }
                    }
                    if (escaped) {
                        continue;
                    }
                }
            } else {
                escaped = false;
                continue;
            }

            switch (state) {
                case NORMAL -> {
                    if (codePoint == '<') {
                        marker = i;
                        state = FirstPassState.TAG;
                    }
                }
                case TAG -> {
                    if (codePoint == '>') {
                        if (i == marker + 1) {
                            state = FirstPassState.NORMAL; // "<>" is not a tag
                        } else {
                            if (currentTokenEnd != marker) {
                                tokens.add(new Token(TokenType.TEXT, currentTokenEnd, marker, null));
                            }
                            currentTokenEnd = i + 1;
                            TokenType type = TokenType.OPEN_TAG;
                            if (marker + 1 < length && message.charAt(marker + 1) == '/') {
                                type = TokenType.CLOSE_TAG;
                            } else if (marker + 2 < length && message.charAt(i - 1) == '/') {
                                type = TokenType.OPEN_CLOSE_TAG;
                            }
                            tokens.add(new Token(type, marker, currentTokenEnd, tagParts(message, type, marker, currentTokenEnd)));
                            state = FirstPassState.NORMAL;
                        }
                    } else if (codePoint == '<') {
                        marker = i; // not a tag, but one may start here
                    } else if (codePoint == '\'' || codePoint == '"') {
                        currentStringChar = (char) codePoint;
                        if (message.indexOf(codePoint, i + 1) != -1) {
                            state = FirstPassState.STRING;
                        }
                    }
                }
                case STRING -> {
                    if (codePoint == currentStringChar) {
                        state = FirstPassState.TAG;
                    }
                }
            }

            if (i == length - 1 && state == FirstPassState.TAG) {
                // an unterminated '<': rescan everything after it as normal text
                i = marker;
                state = FirstPassState.NORMAL;
            }
        }

        int end = tokens.isEmpty() ? -1 : tokens.get(tokens.size() - 1).end();
        if (end == -1) {
            tokens.add(new Token(TokenType.TEXT, 0, length, null));
        } else if (end != length) {
            tokens.add(new Token(TokenType.TEXT, end, length, null));
        }
        return tokens;
    }

    /** Splits a tag token into its name and {@code :}-separated arguments (MiniMessage's second pass). */
    private static List<int[]> tagParts(String message, TokenType type, int tokenStart, int tokenEnd) {
        int startIndex = type == TokenType.CLOSE_TAG ? tokenStart + 2 : tokenStart + 1;
        int endIndex = type == TokenType.OPEN_CLOSE_TAG ? tokenEnd - 2 : tokenEnd - 1;
        List<int[]> parts = new ArrayList<>();

        boolean inString = false;
        boolean escaped = false;
        char currentStringChar = 0;
        int marker = startIndex;
        for (int i = startIndex; i < endIndex; i++) {
            char c = message.charAt(i);
            if (!escaped) {
                if (c == '\\' && i + 1 < message.length()) {
                    char next = message.charAt(i + 1);
                    escaped = inString
                            ? currentStringChar == next || next == '\\'
                            : next == '<' || next == '\\';
                    if (escaped) {
                        continue;
                    }
                }
            } else {
                escaped = false;
                continue;
            }

            if (!inString) {
                if (c == ':') {
                    // values are split by ':' unless it's part of a URL
                    if (i + 2 < message.length() && message.charAt(i + 1) == '/' && message.charAt(i + 2) == '/') {
                        continue;
                    }
                    if (marker == i) {
                        parts.add(new int[]{i, i});
                        marker++;
                    } else {
                        parts.add(new int[]{marker, i});
                        marker = i + 1;
                    }
                } else if (c == '\'' || c == '"') {
                    inString = true;
                    currentStringChar = c;
                }
            } else if (c == currentStringChar) {
                inString = false;
            }
        }

        if (parts.isEmpty()) {
            parts.add(new int[]{startIndex, endIndex});
        } else {
            int end = parts.get(parts.size() - 1)[1];
            if (end != endIndex) {
                parts.add(new int[]{end + 1, endIndex});
            }
        }
        return parts;
    }

    /** Removes a {@code \} in front of any character accepted by {@code escapes}. */
    private static String unescape(String text, int startIndex, int endIndex, IntPredicate escapes) {
        int from = startIndex;
        int i = text.indexOf('\\', from);
        if (i == -1 || i >= endIndex) {
            return text.substring(from, endIndex);
        }

        StringBuilder sb = new StringBuilder(endIndex - startIndex);
        while (i != -1 && i + 1 < endIndex) {
            if (escapes.test(text.codePointAt(i + 1))) {
                sb.append(text, from, i);
                i++;
                if (i >= endIndex) {
                    from = endIndex;
                    break;
                }
                int codePoint = text.codePointAt(i);
                sb.appendCodePoint(codePoint);
                i += Character.isBmpCodePoint(codePoint) ? 1 : 2;
                if (i >= endIndex) {
                    from = endIndex;
                    break;
                }
            } else {
                i++;
                sb.append(text, from, i);
            }
            from = i;
            i = text.indexOf('\\', from);
        }
        sb.append(text, from, endIndex);
        return sb.toString();
    }

    private static String unquoteAndEscape(String text, int start, int end) {
        if (start == end) {
            return "";
        }
        int startIndex = start;
        int endIndex = end;
        char firstChar = text.charAt(startIndex);
        char lastChar = text.charAt(endIndex - 1);
        if (firstChar == '\'' || firstChar == '"') {
            startIndex++;
        } else {
            return text.substring(startIndex, endIndex);
        }
        if (lastChar == '\'' || lastChar == '"') {
            endIndex--;
        }
        if (startIndex > endIndex) {
            return text.substring(start, end);
        }
        return unescape(text, startIndex, endIndex, c -> c == firstChar || c == '\\');
    }

    private static String textValue(String message, Token token) {
        return unescape(message, token.start(), token.end(), c -> c == '<' || c == '\\');
    }

    // ---- tree -----------------------------------------------------------------------

    private sealed interface Tag permits StyleTag, GradientTag, InsertTag, ResetTag {
    }

    /** Colour and/or decorations applied to the children ({@code color} -1 = unset). */
    private record StyleTag(int color, Boolean[] decorations) implements Tag {
    }

    private record GradientTag(int[] colors, double phase) implements Tag {
    }

    /** Self-closing literal text: placeholders and newlines. */
    private record InsertTag(String text) implements Tag {
    }

    private enum ResetTag implements Tag { INSTANCE }

    private static class Element {
        final Element parent;
        final List<Element> children = new ArrayList<>();

        Element(Element parent) {
            this.parent = parent;
        }
    }

    private static final class TextElement extends Element {
        final String value;

        TextElement(Element parent, String value) {
            super(parent);
            this.value = value;
        }
    }

    private static final class TagElement extends Element {
        final List<String> parts;
        final Tag tag;

        TagElement(Element parent, List<String> parts, Tag tag) {
            super(parent);
            this.parts = parts;
            this.tag = tag;
        }
    }

    private static Element buildTree(String message, List<Token> tokens, Map<String, String> placeholders) {
        Element root = new Element(null);
        Element node = root;

        for (Token token : tokens) {
            switch (token.type()) {
                case TEXT -> node.children.add(new TextElement(node, textValue(message, token)));

                case OPEN_TAG, OPEN_CLOSE_TAG -> {
                    int[] namePart = token.parts().get(0);
                    String rawName = message.substring(namePart[0], namePart[1]);
                    if (!TAG_NAME.matcher(rawName.toLowerCase(Locale.ROOT)).matches()) {
                        node.children.add(new TextElement(node, textValue(message, token)));
                        break;
                    }
                    List<String> parts = new ArrayList<>();
                    for (int[] part : token.parts()) {
                        parts.add(unquoteAndEscape(message, part[0], part[1]));
                    }
                    Tag tag = resolve(parts.get(0).toLowerCase(Locale.ROOT), parts.subList(1, parts.size()), placeholders);
                    if (tag == null) {
                        // unknown tag (or bad arguments): MiniMessage keeps it as literal text
                        node.children.add(new TextElement(node, textValue(message, token)));
                    } else if (tag == ResetTag.INSTANCE) {
                        node = root; // closes everything that is open
                    } else {
                        TagElement tagElement = new TagElement(node, parts, tag);
                        node.children.add(tagElement);
                        if (token.type() != TokenType.OPEN_CLOSE_TAG && !(tag instanceof InsertTag)) {
                            node = tagElement;
                        }
                    }
                }

                case CLOSE_TAG -> {
                    List<String> closeValues = new ArrayList<>();
                    for (int[] part : token.parts()) {
                        closeValues.add(unquoteAndEscape(message, part[0], part[1]));
                    }
                    if (closeValues.get(0).equals("reset") && !placeholders.containsKey("reset")) {
                        continue; // closing a <reset> means nothing
                    }
                    Element parentNode = node;
                    while (parentNode instanceof TagElement open) {
                        if (tagCloses(closeValues, open.parts)) {
                            node = open.parent;
                            break;
                        }
                        parentNode = parentNode.parent;
                    }
                    if (!(parentNode instanceof TagElement)) {
                        // didn't match any open tag: literal text
                        node.children.add(new TextElement(node, textValue(message, token)));
                    }
                }
            }
        }
        return root;
    }

    private static boolean tagCloses(List<String> closeParts, List<String> openParts) {
        if (closeParts.size() > openParts.size()) {
            return false;
        }
        if (!closeParts.get(0).equalsIgnoreCase(openParts.get(0))) {
            return false;
        }
        for (int i = 1; i < closeParts.size(); i++) {
            if (!closeParts.get(i).equals(openParts.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** The tag called {@code name} (already lower-cased), or {@code null} if unknown/invalid. */
    private static Tag resolve(String name, List<String> args, Map<String, String> placeholders) {
        // Placeholders win over built-in tags; given arguments they don't apply and the
        // built-in tag of that name (if any) is used instead, as in MiniMessage.
        if (args.isEmpty() && placeholders.containsKey(name)) {
            return new InsertTag(placeholders.get(name));
        }
        if (name.equals("color") || name.equals("colour") || name.equals("c")) {
            if (args.isEmpty() || args.get(0).isEmpty()) {
                return null;
            }
            int color = colorOrMinusOne(args.get(0).toLowerCase(Locale.ROOT));
            return color == -1 ? null : colorTag(color);
        }
        if (!name.isEmpty()) {
            int color = colorOrMinusOne(name);
            if (color != -1) {
                return colorTag(color);
            }
        }

        boolean negated = name.startsWith("!");
        String decorationName = negated ? name.substring(1) : name;
        for (int d = 0; d < DECORATION_NAMES.length; d++) {
            if (Arrays.asList(DECORATION_NAMES[d]).contains(decorationName)) {
                if (negated && !args.isEmpty()) {
                    return null; // <!bold> takes no arguments
                }
                boolean state = !negated
                        && (args.isEmpty() || !(args.get(0).equals("false") || args.get(0).equals("off")));
                Boolean[] decorations = new Boolean[DECORATION_CODES.length];
                decorations[d] = state;
                return new StyleTag(-1, decorations);
            }
        }

        switch (name) {
            case "gradient":
                return gradient(args);
            case "newline", "br":
                return new InsertTag("\n");
            default:
                break;
        }

        // <reset> takes no arguments; with any it is literal text
        return name.equals("reset") && args.isEmpty() ? ResetTag.INSTANCE : null;
    }

    private static StyleTag colorTag(int color) {
        return new StyleTag(color, new Boolean[DECORATION_CODES.length]);
    }

    /** A named colour ({@code grey}/{@code dark_grey} too) or {@code #hex}, else -1. */
    private static int colorOrMinusOne(String name) {
        if (name.equals("grey")) {
            return COLOR_VALUES[7];
        }
        if (name.equals("dark_grey")) {
            return COLOR_VALUES[8];
        }
        if (name.charAt(0) == '#') {
            try {
                return Integer.parseInt(name.substring(1), 16) & 0xffffff;
            } catch (NumberFormatException ex) {
                return -1;
            }
        }
        int index = Arrays.asList(COLOR_NAMES).indexOf(name);
        return index == -1 ? -1 : COLOR_VALUES[index];
    }

    private static GradientTag gradient(List<String> args) {
        double phase = 0;
        List<Integer> colors = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            int color = arg.isEmpty() ? -1 : colorOrMinusOne(arg); // arguments are case-sensitive here
            if (color != -1) {
                colors.add(color);
                continue;
            }
            if (i == args.size() - 1) {
                try {
                    phase = Double.parseDouble(arg);
                } catch (NumberFormatException ex) {
                    return null;
                }
                if (phase < -1d || phase > 1d) {
                    return null;
                }
                break;
            }
            return null;
        }
        if (colors.size() == 1) {
            return null; // MiniMessage requires at least two colours
        }
        if (colors.isEmpty()) {
            colors = List.of(0xffffff, 0x000000);
        }
        return new GradientTag(colors.stream().mapToInt(Integer::intValue).toArray(), phase);
    }

    // ---- rendering ------------------------------------------------------------------

    /** Effective style; {@code color} -1 = default, decorations {@code null} = not set. */
    private record Style(int color, Boolean[] decorations) {
        static final Style EMPTY = new Style(-1, new Boolean[DECORATION_CODES.length]);

        Style merge(StyleTag tag) {
            Boolean[] merged = decorations.clone();
            for (int d = 0; d < merged.length; d++) {
                if (tag.decorations()[d] != null) {
                    merged[d] = tag.decorations()[d];
                }
            }
            return new Style(tag.color() != -1 ? tag.color() : color, merged);
        }
    }

    private record StyledChar(int codePoint, int color, boolean[] decorations) {
    }

    /** Running state of one {@code <gradient>}, mirroring MiniMessage's GradientTag. */
    private static final class GradientState {
        final int[] colors;
        final double multiplier;
        final double phase;
        int index;

        GradientState(GradientTag tag, int size) {
            int[] colors = tag.colors().clone();
            double phase = tag.phase();
            if (phase < 0) {
                phase = 1 + phase;
                int[] reversed = new int[colors.length];
                for (int i = 0; i < colors.length; i++) {
                    reversed[i] = colors[colors.length - 1 - i];
                }
                colors = reversed;
            }
            this.colors = colors;
            this.multiplier = size == 1 ? 0 : (double) (colors.length - 1) / (size - 1);
            this.phase = phase * (colors.length - 1);
        }

        int color() {
            double position = (index * multiplier) + phase;
            int lowUnclamped = (int) Math.floor(position);
            int high = (int) Math.ceil(position) % colors.length;
            int low = lowUnclamped % colors.length;
            return lerp((float) position - lowUnclamped, colors[low], colors[high]);
        }

        private static int lerp(float t, int a, int b) {
            float clampedT = Math.min(1.0f, Math.max(0.0f, t));
            int r = Math.round(((a >> 16) & 0xff) + clampedT * (((b >> 16) & 0xff) - ((a >> 16) & 0xff)));
            int g = Math.round(((a >> 8) & 0xff) + clampedT * (((b >> 8) & 0xff) - ((a >> 8) & 0xff)));
            int bl = Math.round((a & 0xff) + clampedT * ((b & 0xff) - (a & 0xff)));
            return (r & 0xff) << 16 | (g & 0xff) << 8 | bl & 0xff;
        }
    }

    /** Code points a gradient spreads its colours over: all text and inserted text below it. */
    private static int gradientSize(Element element) {
        int size = 0;
        if (element instanceof TextElement text) {
            size += text.value.codePointCount(0, text.value.length());
        } else if (element instanceof TagElement tag && tag.tag instanceof InsertTag insert) {
            size += insert.text().codePointCount(0, insert.text().length());
        }
        for (Element child : element.children) {
            size += gradientSize(child);
        }
        return size;
    }

    /**
     * Emits every code point with its effective style. The colour comes from the nearest
     * colour-setting ancestor ({@code colorSource}: a fixed-colour style, or a gradient);
     * every enclosing gradient advances once per code point, coloured by it or not.
     */
    private static void flatten(Element element, Style style, GradientState colorSource,
                                List<GradientState> gradients, List<StyledChar> out) {
        if (element instanceof TextElement text) {
            emit(text.value, style, colorSource, gradients, out);
        } else if (element instanceof TagElement tag) {
            switch (tag.tag) {
                case InsertTag insert -> emit(insert.text(), style, colorSource, gradients, out);
                case StyleTag styleTag -> {
                    Style childStyle = style.merge(styleTag);
                    GradientState childSource = styleTag.color() != -1 ? null : colorSource;
                    for (Element child : element.children) {
                        flatten(child, childStyle, childSource, gradients, out);
                    }
                }
                case GradientTag gradientTag -> {
                    GradientState gradient = new GradientState(gradientTag, gradientSize(element));
                    List<GradientState> inner = new ArrayList<>(gradients);
                    inner.add(gradient);
                    for (Element child : element.children) {
                        flatten(child, style, gradient, inner, out);
                    }
                }
                case ResetTag reset -> {
                }
            }
        } else {
            for (Element child : element.children) {
                flatten(child, style, colorSource, gradients, out);
            }
        }
    }

    private static void emit(String text, Style style, GradientState colorSource,
                             List<GradientState> gradients, List<StyledChar> out) {
        boolean[] decorations = new boolean[DECORATION_CODES.length];
        for (int d = 0; d < decorations.length; d++) {
            decorations[d] = Boolean.TRUE.equals(style.decorations()[d]);
        }
        text.codePoints().forEach(codePoint -> {
            int color = colorSource != null ? colorSource.color() : style.color();
            for (GradientState gradient : gradients) {
                gradient.index++;
            }
            out.add(new StyledChar(codePoint, color, decorations));
        });
    }

    /**
     * Serialises styled code points to legacy text. A colour code (or {@code §r}) clears
     * decorations, so the colour is re-sent whenever one must be switched off.
     */
    private static String toLegacy(List<StyledChar> chars) {
        StringBuilder sb = new StringBuilder();
        int currentColor = -1;
        boolean[] current = new boolean[DECORATION_CODES.length];
        boolean known = true; // chat starts with the default (uncoloured, undecorated) style
        for (StyledChar c : chars) {
            if (c.codePoint() == '\n') {
                sb.append('\n');
                known = false; // re-send the full style on the next line
                continue;
            }
            boolean dropsDecoration = false;
            for (int d = 0; d < current.length; d++) {
                dropsDecoration |= current[d] && !c.decorations()[d];
            }
            if (!known || c.color() != currentColor || dropsDecoration) {
                appendColor(sb, c.color());
                current = new boolean[DECORATION_CODES.length];
            }
            for (int d = 0; d < current.length; d++) {
                if (c.decorations()[d] && !current[d]) {
                    sb.append(SECTION).append(DECORATION_CODES[d]);
                }
            }
            current = c.decorations();
            currentColor = c.color();
            known = true;
            sb.appendCodePoint(c.codePoint());
        }
        return sb.toString();
    }

    private static void appendColor(StringBuilder sb, int color) {
        if (color == -1) {
            sb.append(SECTION).append('r');
            return;
        }
        for (int i = 0; i < COLOR_VALUES.length; i++) {
            if (COLOR_VALUES[i] == color) {
                sb.append(SECTION).append(Character.forDigit(i, 16));
                return;
            }
        }
        sb.append(SECTION).append('x');
        for (char digit : String.format("%06x", color).toCharArray()) {
            sb.append(SECTION).append(digit);
        }
    }
}
