package org.example.healthcare.common;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal, dependency-free PDF generator.
 *
 * <p>Emits a single-page A4 PDF using the two built-in (base-14) Helvetica fonts, so no font
 * embedding or external library is required. Intended for simple text documents such as
 * prescriptions. Text is laid out top-to-bottom with absolute positioning and basic word-wrap.
 *
 * <p>This is deliberately small rather than a general PDF toolkit: it supports plain text lines
 * in regular or bold weight. Non-Latin-1 characters (e.g. Romanian diacritics) are transliterated
 * to their closest ASCII equivalent because the standard Helvetica encoding cannot render them.
 */
public final class PdfWriter {

    private PdfWriter() {}

    /** A single line of text. {@code gapBefore} adds extra vertical space above the line (points). */
    public record Line(String text, float size, boolean bold, float gapBefore) {
        public static Line of(String text, float size)            { return new Line(text, size, false, 0f); }
        public static Line bold(String text, float size)          { return new Line(text, size, true, 0f); }
        public static Line of(String text, float size, float gap) { return new Line(text, size, false, gap); }
        public static Line bold(String text, float size, float gap) { return new Line(text, size, true, gap); }
    }

    private static final float PAGE_W  = 595f;   // A4 width  in points
    private static final float PAGE_H  = 842f;   // A4 height in points
    private static final float MARGIN  = 56f;
    private static final float TOP_Y   = 786f;
    private static final float BOTTOM  = 56f;
    private static final int   WRAP    = 92;     // approx chars per line at 11pt

    /**
     * Builds a PDF document from the given lines.
     *
     * @return the complete PDF file as a byte array
     */
    public static byte[] build(List<Line> lines) {
        // ── 1. Build the page content stream ────────────────────────────────
        StringBuilder cs = new StringBuilder();
        float y = TOP_Y;
        for (Line line : lines) {
            y -= line.gapBefore();
            for (String part : wrap(line.text(), WRAP)) {
                if (y < BOTTOM) break; // single page — drop overflow rather than corrupt layout
                String font = line.bold() ? "F2" : "F1";
                cs.append("BT /").append(font).append(' ').append(num(line.size()))
                  .append(" Tf ").append(num(MARGIN)).append(' ').append(num(y))
                  .append(" Td (").append(escape(part)).append(") Tj ET\n");
                y -= line.size() * 1.45f;
            }
        }
        byte[] content = cs.toString().getBytes(StandardCharsets.ISO_8859_1);

        // ── 2. Assemble the PDF objects, tracking byte offsets for the xref ──
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();

        ascii(out, "%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n");

        offsets.add(out.size());
        ascii(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        offsets.add(out.size());
        ascii(out, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

        offsets.add(out.size());
        ascii(out, "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "
                + num(PAGE_W) + " " + num(PAGE_H) + "] "
                + "/Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> "
                + "/Contents 6 0 R >>\nendobj\n");

        offsets.add(out.size());
        ascii(out, "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                + "/Encoding /WinAnsiEncoding >>\nendobj\n");

        offsets.add(out.size());
        ascii(out, "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold "
                + "/Encoding /WinAnsiEncoding >>\nendobj\n");

        offsets.add(out.size());
        ascii(out, "6 0 obj\n<< /Length " + content.length + " >>\nstream\n");
        out.writeBytes(content);
        ascii(out, "\nendstream\nendobj\n");

        // ── 3. Cross-reference table + trailer ──────────────────────────────
        int xrefPos = out.size();
        int size = offsets.size() + 1; // including the mandatory free object 0
        StringBuilder xref = new StringBuilder();
        xref.append("xref\n0 ").append(size).append('\n');
        xref.append("0000000000 65535 f \n");
        for (int off : offsets) {
            xref.append(String.format("%010d 00000 n \n", off));
        }
        xref.append("trailer\n<< /Size ").append(size).append(" /Root 1 0 R >>\n")
            .append("startxref\n").append(xrefPos).append("\n%%EOF");
        ascii(out, xref.toString());

        return out.toByteArray();
    }

    private static void ascii(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    /** Formats a float without a trailing ".0" so PDF numbers stay compact. */
    private static String num(float v) {
        if (v == Math.rint(v)) return Integer.toString((int) v);
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    /** Escapes the PDF string delimiters and transliterates unsupported characters. */
    private static String escape(String text) {
        String s = transliterate(text);
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '('  -> b.append("\\(");
                case ')'  -> b.append("\\)");
                case '\r', '\n' -> b.append(' ');
                default -> b.append(c > 255 ? '?' : c);
            }
        }
        return b.toString();
    }

    /** Replaces Romanian (and a few common) diacritics with ASCII so Helvetica can render them. */
    private static String transliterate(String text) {
        if (text == null) return "";
        return text
                .replace('ă', 'a').replace('â', 'a').replace('î', 'i')
                .replace('ș', 's').replace('ş', 's').replace('ț', 't').replace('ţ', 't')
                .replace('Ă', 'A').replace('Â', 'A').replace('Î', 'I')
                .replace('Ș', 'S').replace('Ş', 'S').replace('Ț', 'T').replace('Ţ', 'T')
                .replace('„', '"').replace('”', '"').replace('’', '\'').replace('–', '-');
    }

    /** Greedy word-wrap to {@code maxChars}; hard-splits any single oversized token. */
    private static List<String> wrap(String text, int maxChars) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            out.add("");
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            while (word.length() > maxChars) {
                if (line.length() > 0) { out.add(line.toString()); line.setLength(0); }
                out.add(word.substring(0, maxChars));
                word = word.substring(maxChars);
            }
            if (line.length() == 0) {
                line.append(word);
            } else if (line.length() + 1 + word.length() <= maxChars) {
                line.append(' ').append(word);
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
