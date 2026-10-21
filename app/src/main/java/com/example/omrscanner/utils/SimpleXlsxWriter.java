package com.example.omrscanner.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Minimal single-sheet .xlsx writer with no third-party dependencies.
 * An .xlsx file is just a zip of XML parts, so java.util.zip is enough for a
 * simple table. Text is written as inline strings (no sharedStrings part).
 */
public final class SimpleXlsxWriter {

    // Style ids (index into cellXfs in styles.xml below)
    public static final int STYLE_DEFAULT     = 0;
    public static final int STYLE_BOLD        = 1;
    public static final int STYLE_TITLE       = 2;
    public static final int STYLE_HEADER      = 3;
    public static final int STYLE_CELL_LEFT   = 4;
    public static final int STYLE_CELL_CENTER = 5;

    /** One cell: text, number, or blank (blank still carries a style, e.g. a bordered empty cell). */
    public static final class Cell {
        final String text;
        final Double number;
        final int style;

        private Cell(String text, Double number, int style) {
            this.text = text;
            this.number = number;
            this.style = style;
        }

        public static Cell text(String value, int style)  { return new Cell(value == null ? "" : value, null, style); }
        public static Cell number(double value, int style) { return new Cell(null, value, style); }
        public static Cell blank(int style)                { return new Cell(null, null, style); }
    }

    private final String sheetName;
    private final List<Cell[]> rows = new ArrayList<>();
    private double[] columnWidths = new double[0];
    private int frozenRows = 0;

    public SimpleXlsxWriter(String sheetName) {
        this.sheetName = sheetName;
    }

    /** Adds a row. Call with no arguments for an empty spacer row. */
    public void addRow(Cell... cells) { rows.add(cells); }

    public void setColumnWidths(double... widths) { this.columnWidths = widths; }

    /** Freezes the first n rows so they stay visible while scrolling. */
    public void freezeRows(int n) { this.frozenRows = n; }

    public void write(OutputStream out) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out);
        putEntry(zip, "[Content_Types].xml", CONTENT_TYPES);
        putEntry(zip, "_rels/.rels", ROOT_RELS);
        putEntry(zip, "xl/workbook.xml", workbookXml());
        putEntry(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
        putEntry(zip, "xl/styles.xml", STYLES);
        putEntry(zip, "xl/worksheets/sheet1.xml", sheetXml());
        zip.finish(); // writes the zip trailer without closing the caller's stream
        zip.flush();
    }

    // ── XML builders ────────────────────────────────────────────

    private String workbookXml() {
        return XML_HEADER
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"" + escape(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                + "</workbook>";
    }

    private String sheetXml() {
        StringBuilder sb = new StringBuilder(XML_HEADER);
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");

        sb.append("<sheetViews><sheetView workbookViewId=\"0\">");
        if (frozenRows > 0) {
            sb.append("<pane ySplit=\"").append(frozenRows).append("\" topLeftCell=\"A")
                    .append(frozenRows + 1).append("\" activePane=\"bottomLeft\" state=\"frozen\"/>");
        }
        sb.append("</sheetView></sheetViews>");
        sb.append("<sheetFormatPr defaultRowHeight=\"15\"/>");

        if (columnWidths.length > 0) {
            sb.append("<cols>");
            for (int i = 0; i < columnWidths.length; i++) {
                sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                        .append("\" width=\"").append(columnWidths[i]).append("\" customWidth=\"1\"/>");
            }
            sb.append("</cols>");
        }

        sb.append("<sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            Cell[] row = rows.get(r);
            if (row.length == 0) continue; // spacer row: simply skipped, row numbers stay correct
            int rowNum = r + 1;
            sb.append("<row r=\"").append(rowNum).append("\">");
            for (int c = 0; c < row.length; c++) {
                Cell cell = row[c];
                String ref = columnLetter(c) + rowNum;
                if (cell.number != null) {
                    sb.append("<c r=\"").append(ref).append("\" s=\"").append(cell.style).append("\"><v>")
                            .append(formatNumber(cell.number)).append("</v></c>");
                } else if (cell.text != null && !cell.text.isEmpty()) {
                    sb.append("<c r=\"").append(ref).append("\" s=\"").append(cell.style)
                            .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(escape(cell.text)).append("</t></is></c>");
                } else {
                    sb.append("<c r=\"").append(ref).append("\" s=\"").append(cell.style).append("\"/>");
                }
            }
            sb.append("</row>");
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    // ── helpers ─────────────────────────────────────────────────

    private static void putEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** 0 -> A, 25 -> Z, 26 -> AA ... */
    static String columnLetter(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        while (n >= 0) {
            sb.insert(0, (char) ('A' + (n % 26)));
            n = n / 26 - 1;
        }
        return sb.toString();
    }

    private static String formatNumber(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
        return Double.toString(d);
    }

    /** Escapes XML special characters and drops characters XML 1.0 does not allow. */
    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&':  sb.append("&amp;");  break;
                case '<':  sb.append("&lt;");   break;
                case '>':  sb.append("&gt;");   break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default:
                    boolean valid = ch == 0x9 || ch == 0xA || ch == 0xD
                            || (ch >= 0x20 && ch <= 0xD7FF)
                            || (ch >= 0xD800 && ch <= 0xDFFF)   // surrogate halves of emoji etc.
                            || (ch >= 0xE000 && ch <= 0xFFFD);
                    if (valid) sb.append(ch);
            }
        }
        return sb.toString();
    }

    // ── static package parts ────────────────────────────────────

    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";

    private static final String CONTENT_TYPES = XML_HEADER
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
            + "</Types>";

    private static final String ROOT_RELS = XML_HEADER
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>";

    private static final String WORKBOOK_RELS = XML_HEADER
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
            + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
            + "</Relationships>";

    private static final String STYLES = XML_HEADER
            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<fonts count=\"4\">"
            + "<font><sz val=\"11\"/><name val=\"Arial\"/></font>"
            + "<font><b/><sz val=\"11\"/><name val=\"Arial\"/></font>"
            + "<font><b/><sz val=\"14\"/><name val=\"Arial\"/></font>"
            + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Arial\"/></font>"
            + "</fonts>"
            + "<fills count=\"3\">"
            + "<fill><patternFill patternType=\"none\"/></fill>"
            + "<fill><patternFill patternType=\"gray125\"/></fill>"
            + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1F4E78\"/><bgColor indexed=\"64\"/></patternFill></fill>"
            + "</fills>"
            + "<borders count=\"2\">"
            + "<border><left/><right/><top/><bottom/><diagonal/></border>"
            + "<border>"
            + "<left style=\"thin\"><color rgb=\"FFBFBFBF\"/></left>"
            + "<right style=\"thin\"><color rgb=\"FFBFBFBF\"/></right>"
            + "<top style=\"thin\"><color rgb=\"FFBFBFBF\"/></top>"
            + "<bottom style=\"thin\"><color rgb=\"FFBFBFBF\"/></bottom>"
            + "<diagonal/></border>"
            + "</borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"6\">"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
            + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
            + "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"left\"/></xf>"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\"/></xf>"
            + "</cellXfs>"
            + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
            + "</styleSheet>";
}