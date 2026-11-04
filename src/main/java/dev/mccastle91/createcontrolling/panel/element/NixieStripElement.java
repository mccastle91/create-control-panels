package dev.mccastle91.createcontrolling.panel.element;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A row of nixie tubes showing one line, a character per tube. The size is
 * the number of tubes.
 */
public class NixieStripElement extends ReadoutElement {

    public static final int MIN_TUBES = 2;
    public static final int MAX_TUBES = 5;
    public static final int DEFAULT_TUBES = 4;
    /** Tube width and the gap either side of each, px. */
    public static final float TUBE = 2f;
    public static final float GAP = 0.5f;

    /** Sign, whole part (commas only as thousands), decimals, then a unit with no digits in it. */
    private static final Pattern NUMBER =
            Pattern.compile("([<>+-]*)(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d+))?(\\D*)");

    private String fittedText;
    private String fitted;

    public NixieStripElement(PanelElementType<? extends NixieStripElement> type) {
        super(type, 1, DEFAULT_TUBES);
    }

    @Override
    public int minSize() {
        return MIN_TUBES;
    }

    @Override
    public int maxSize() {
        return MAX_TUBES;
    }

    @Override
    public float widthFor(int size) {
        return GAP + size * (TUBE + GAP);
    }

    @Override
    public int columns() {
        return size();
    }

    /** What the tubes show for this text, a character each; drawn every frame, so the last answer is kept. */
    public String glyphs(String text) {
        if (!text.equals(fittedText) || fitted.length() != size()) {
            fittedText = text;
            fitted = fit(text, size());
        }
        return fitted;
    }

    /**
     * Fits text to exactly {@code tubes} characters, right-aligned. A number
     * gives up decimals (rounding), then its unit; one that still won't fit
     * shows dashes, since dropping digits would show the wrong value. Other
     * text is simply cut off.
     */
    public static String fit(String text, int tubes) {
        String compact = text.replaceAll("[\\s\\u00A0]", "");
        if (compact.length() <= tubes)
            return padded(compact, tubes);
        Matcher number = NUMBER.matcher(compact);
        if (!number.matches())
            return compact.substring(0, tubes);
        String sign = number.group(1), unit = number.group(4);
        String decimals = number.group(3) == null ? "" : number.group(3);
        BigDecimal value = new BigDecimal(number.group(2).replace(",", "") + "." + decimals + "0");
        for (int scale = decimals.length(); scale >= 0; scale--) {
            String candidate = sign + value.setScale(scale, RoundingMode.HALF_UP).toPlainString() + unit;
            if (candidate.length() <= tubes)
                return padded(candidate, tubes);
        }
        String whole = sign + value.setScale(0, RoundingMode.HALF_UP).toPlainString();
        return whole.length() <= tubes ? padded(whole, tubes) : "-".repeat(tubes);
    }

    private static String padded(String text, int tubes) {
        return " ".repeat(tubes - text.length()) + text;
    }
}
