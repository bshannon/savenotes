/*
 * Copyright (c) 2019 Bill Shannon. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of the copyright holder nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.io.*;
import java.sql.*;
import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Save Apple Notes content.
 *
 * @author Bill Shannon
 */
public class SaveNotes {
    private static final String NOTES_DB =
        "Library/Group Containers/group.com.apple.notes/NoteStore.sqlite";
    private static final String query =
        "SELECT n.Z_PK as pk, " +
        " n.ZNOTE as note_id, " +
        " n.ZDATA as data, " +
        /*
        " c3.ZFILESIZE, " +
        " c4.ZFILENAME, " +
        " c4.ZIDENTIFIER as att_uuid,  " +
        */
        " c1.ZTITLE1 as title, " +
        " c1.ZSNIPPET as snippet, " +
        " c1.ZIDENTIFIER as noteID, " +
        " c1.ZCREATIONDATE1 as created, " +
        " c1.ZLASTVIEWEDMODIFICATIONDATE as lastviewed, " +
        " c1.ZMODIFICATIONDATE1 as modified, " +
        " c2.ZACCOUNT3, " +
        " c2.ZTITLE2 as folderName, " +
        " c2.ZIDENTIFIER as folderID " +
        /*
        " c5.ZNAME as acc_name, " +
        " c5.ZIDENTIFIER as acc_identifier, " +
        " c5.ZACCOUNTTYPE " +
        */
        " FROM ZICNOTEDATA as n " +
        " LEFT JOIN ZICCLOUDSYNCINGOBJECT as c1 ON c1.ZNOTEDATA = n.Z_PK  " +
        " LEFT JOIN ZICCLOUDSYNCINGOBJECT as c2 ON c2.Z_PK = c1.ZFOLDER " +
        /*
        " LEFT JOIN ZICCLOUDSYNCINGOBJECT as c3 ON c3.ZNOTE = n.ZNOTE " +
        " LEFT JOIN ZICCLOUDSYNCINGOBJECT as c4 ON c4.ZATTACHMENT1 = c3.Z_PK " +
        " LEFT JOIN ZICCLOUDSYNCINGOBJECT as c5 ON c5.Z_PK = c1.ZACCOUNT2  " +
        */
        " ORDER BY note_id";
        // XXX - commented above returns multiple records for a given note

        // folderName == NULL implies this note has been deleted?

        /*
         * ZICCLOUDSYNCINGOBJECT.ZIDENTIFIER matches uuid and
         * ZICCLOUDSYNCINGOBJECT.ZTYPEUTI matches type, then
         * ZICCLOUDSYNCINGOBJECT.ZMERGEABLEDATA contains gzipped
         * table data, including "schema", in archived object format.
         */


    private static boolean verbose;
    private static boolean all;
    private static boolean html;
    private static boolean raw;
    private static boolean marked;
    private static boolean print;
    private static boolean markdown;
    private static boolean debug;
    private static Pattern titlePat;
    private static File root;
    private static String db = null;

    public static void main(String[] argv) throws Exception {

        int optind;
        for (optind = 0; optind < argv.length; optind++) {
            if (argv[optind].equals("-f")) {
                db = argv[++optind];
            } else if (argv[optind].equals("-a")) {
                all = true;
            } else if (argv[optind].equals("-v")) {
                verbose = true;
            } else if (argv[optind].equals("-d")) {
                root = new File(argv[++optind]);
            } else if (argv[optind].equals("-t")) {
                titlePat = Pattern.compile(argv[++optind]);
            } else if (argv[optind].equals("-h")) {
                html = true;
            } else if (argv[optind].equals("-r")) {
                raw = true;
            } else if (argv[optind].equals("-k")) {
                marked = true;
            } else if (argv[optind].equals("-p")) {
                print = true;
            } else if (argv[optind].equals("-m")) {
                markdown = true;
            } else if (argv[optind].equals("-X")) {
                debug = true;
            } else if (argv[optind].equals("--")) {
                optind++;
                break;
            } else if (argv[optind].startsWith("-")) {
                System.out.println(
                    "Usage: savenotes [-f db] [-a] [-v] [-d dir] [-t pattern]" +
                    " [-h] [-r] [-m] [-p] [-k] [-X]");
                System.exit(1);
            } else {
                break;
            }
        }

        if (db == null) {
            String home = System.getProperty("user.home");
            db = home + File.separator + NOTES_DB;
        }
        if (root == null)
            root = new File(".");

        save();
    }

    /**
     * Connect to the Notes database.
     */
    private static Connection connect(String db) {
        Connection conn = null;
        try {
            String url = "jdbc:sqlite:" + db;

            conn = DriverManager.getConnection(url);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return conn;
    }

    /**
     * Loop through the matching notes and save (or print) each one.
     */
    private static void save() throws SQLException, IOException {
        String ext = raw ? ".raw" : (html ? ".html" : (markdown ? ".md" : ".txt"));

        Connection conn = connect(db);
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);

        for (int row = 1; rs.next(); row++) {
            String folderName = rs.getString("folderName");
            if (!all && folderName == null)
                continue;
            String title = rs.getString("title");
            if (title == null)
                title = "Untitled";
            if (titlePat != null && !titlePat.matcher(title).find())
                continue;

            File note = null;
            if (print) {
                if (verbose)
                    System.out.println("Note: " + title);
            } else {
                title = title.replace('/', '-');
                File dir = new File(root, folderName);
                if (!dir.exists())
                    dir.mkdir();
                note = new File(dir, title + ext);
                for (int i = 1; note.exists(); i++)
                    note = new File(dir, title + "-" + i + ext);
                if (verbose)
                    System.out.println("Save: " + note);
            }

            debug("%d: pk %d, note_id %d, noteID %d: %d %s/%s%n",
                row,
                rs.getInt("pk"),
                rs.getInt("note_id"),
                rs.getInt("noteID"),
                rs.getLong("modified"),
                rs.getString("folderName"),
                rs.getString("title"));

            if (raw) {
                try (FileOutputStream os = new FileOutputStream(note)) {
                    try (InputStream bis = rs.getBinaryStream("data")) {
                        int c;
                        if (bis != null) {
                            InputStream is = new GZIPInputStream(bis);
                            while ((c = is.read()) >= 0)
                                os.write((char)c);
                        }
                    }
                }
            } else if (print) {
                try (InputStream bis = rs.getBinaryStream("data")) {
                    String data = getNoteBody(bis);
                    System.out.println(data);
                }
            } else {
                try (FileWriter fw = new FileWriter(note)) {
                    try (InputStream bis = rs.getBinaryStream("data")) {
                        String data = getNoteBody(bis);
                        fw.write(data);
                    }
                }
            }
        }
    }

    private static final float DEFAULT_FONT_SIZE = 12;

    private static String getNoteBody(InputStream is) throws IOException {
        if (is == null) {
            return "<NO DATA>"; // XXX
        }

        is = new GZIPInputStream(is);
        ArchivedObjectReader nr = new ArchivedObjectReader(is);
        ObjectData nd = nr.next();
        assert nd.index() == 1 && nd.getInt() == 0;
        nd = nr.next();
        assert nd.index() == 2;

        nr = nd.getObject();
        nd = nr.next();
        assert nd.index() == 1 && nd.getInt() == 0;
        nd = nr.next();
        assert nd.index() == 2 && nd.getInt() == 0;
        nd = nr.next();
        assert nd.index() == 3;

        nr = nd.getObject();
        nd = nr.next();
        assert nd.index() == 2;
        String text = nd.getString();
        debug("Text len: %d%n", text.length());
        debug("Text:%n%s%n", text);
        
        if (!marked && !html && !markdown)
            return text;

        /*
         * Loop through the "edit record" elements.
         * This list grows every time an edit is made.
         * The positions in this list refer to positions
         * that don't exist in the current string.
         */
        for (;;) {
            nd = nr.next();
            assert nd.index() == 3;
            ArchivedObjectReader rr = nd.getObject();
            //debug("EditRec: %s%n", bytesToHex(rr.getBytes()));
            nd = rr.next();
            assert nd.index() == 1;

            ArchivedObjectReader rr2 = nd.getObject();
            ObjectData nd2 = rr2.next();
            assert nd2.index() == 1;
            int flag = nd2.getInt(); // == 0 -> first or last, else 1
            nd2 = rr2.next();
            assert nd2.index() == 2;
            int pos = nd2.getInt();
            if (pos < 0) {
                debug("Edit rec: flag %d pos %d%n", flag, pos);
                break;
            }

            nd = rr.next();
            assert nd.index() == 2;
            int len = nd.getInt();
            nd = rr.next();
            assert nd.index() == 3;

            ArchivedObjectReader nra = nd.getObject();
            ObjectData nda = nra.next();
            assert nda.index() == 1;
            int s1 = nda.getInt();
            nda = nra.next();
            assert nda.index() == 2;
            int i1 = nda.getInt();
            nda = nra.next();

            boolean f2 = false;
            int next = -1;
            int next2 = -1;

            nd = rr.next();
            if (nd != null) {
                if (nd.index() == 4) {
                    f2 = nd.getBoolean();
                    nd = rr.next();
                }

                // I think this is really a "next" pointer in a linked list,
                // although they're always in order when archived
                assert nd.index() == 5;
                next = nd.getInt();
                nd = rr.next();
            }

            if (nd != null) {
                assert nd.index() == 5;
                next2 = nd.getInt();
                nd = rr.next();
            }

            assert nd == null;

            // these elements don't seem to matter
            debug("Edit rec: " +
                "flag %d pos %d len %d s1 %d i1 %d f2 %b next %d next2 %d%n",
                flag, pos, len, s1, i1, f2, next, next2);

        }

        nd = nr.next();
        assert nd.index() == 4;

        ArchivedObjectReader nr2 = nd.getObject();
        ObjectData nd2 = nr2.next();
        assert nd2.index() == 1;

        ArchivedObjectReader nr3 = nd2.getObject();
        ObjectData nd3 = nr3.next();
        assert nd3.index() == 1;
        byte[] ba4 = nd3.getBytes();
        debug("Unknown: %s%n", bytesToHex(ba4));

        int v1s = -1, v2s = -1;
        nd3 = nr3.next();
        assert nd3.index() == 2;
        ArchivedObjectReader v1 = nd3.getObject();
        ObjectData v1d = v1.next();
        if (v1d != null) {
            assert v1d.index() == 1;
            v1s = v1d.getInt();
        }
        nd3 = nr3.next();
        assert nd3.index() == 2;
        ArchivedObjectReader v2 = nd3.getObject();
        ObjectData v2d = v2.next();
        if (v2d != null) {
            assert v2d.index() == 1;
            v2s = v2d.getInt();
        }
        debug("Vers: %02x %02x%n", v1s, v2s);

        /*
         * Loop through the "attribute" elements.
         */
        int totalLen = 0;
        List<Attribute> attributes = new ArrayList<>();
        for (int attrNum = 1; (nd = nr.next()) != null; attrNum++) {
            assert nd.index() == 5;
            // these are the individual style elements that are attached to
            // an attribute
            int style = 0;
            String fname = null;
            float fsize = DEFAULT_FONT_SIZE;
            Color color = null;
            String uuid = null;
            String utype = null;
            String url = null;
            int parastyle = -1;
            boolean checked = false;
            int indent = 0;

            debug("Attr: %s%n", bytesToHex(nd.getBytes()));
            ArchivedObjectReader anr = nd.getObject();
            ObjectData ad = anr.next();
            assert ad.index() == 1;
            int len = ad.getInt();
            debug("Attr %d: len %d%n\t", attrNum, len);
            totalLen += len;
            // much of the following data is optional
            aloop:
            while ((ad = anr.next()) != null) {
                switch (ad.index()) {
                case 2:
                    // paragraph style
                    ArchivedObjectReader psr = ad.getObject();
                    int paraflag = -1;  // XXX - don't know what this is
                    int p2 = -1;        // XXX - don't know what this is
                    int p7 = -1;        // XXX - don't know what this is
                    ObjectData psd;
                    psloop:
                    while ((psd = psr.next()) != null) {
                        switch (psd.index()) {
                        case 1:
                            parastyle = psd.getInt();
                            break;
                        case 2:
                            p2 = psd.getInt();
                            break;
                        case 3:
                            paraflag = psd.getInt();
                            break;
                        case 4:
                            indent = psd.getInt();
                            debug("indent %d ", indent);
                            break;
                        case 5:
                            // a nested struct with another nested struct
                            // and an int; don't know what this is
                            ArchivedObjectReader psr2 = psd.getObject();
                            ObjectData psd2 = psr2.next();
                            assert psd2.index() == 1;
                            debug("para5 bytes [%s] ",
                                    bytesToHex(psd2.getBytes()));
                            psd2 = psr2.next();
                            assert psd2.index() == 2;
                            checked = psd2.getBoolean();
                            debug("checked %b ", checked);
                            break;
                        case 7:
                            p7 = psd.getInt();
                            break;
                        default:
                            debug("para bytes [%s] ",
                                    bytesToHex(psd.getBytes()));
                            err("Unexpected paragraph data %d", psd.index());
                            break psloop;
                        }
                    }
                    debug("parastyle %d paraflag %d p2 %d p7 %d ",
                                        parastyle, paraflag, p2, p7);
                    break;
                case 3:
                    ArchivedObjectReader fnr = ad.getObject();
                    ObjectData fnd;
                    floop:
                    while ((fnd = fnr.next()) != null) {
                        switch (fnd.index()) {
                        case 1:
                            if (fname != null)
                                err("Already saw font-name %s", fname);
                            fname = fnd.getString();
                            debug("font-name %s ", fname);
                            break;
                        case 2:
                            if (fsize != DEFAULT_FONT_SIZE)
                                err("Already saw font-size %f", fsize);
                            fsize = fnd.getFloat();
                            debug("font-size %s ", fontSize(fsize));
                            break;
                        case 3:
                            int fb = fnd.getInt();
                            // this always has the value 1, or doesn't exist
                            debug("font-byte %d ", fb);
                            break;
                        default:
                            err("Unexpected font attribute %s",
                                    bytesToHex(fnd.getBytes()));
                            break floop;
                        }
                    }
                    break;
                case 5:
                    if (style != 0)
                        err("Already saw style %d", style);
                    style = ad.getInt();
                    debug("style %x ", style);
                    break;
                case 6:
                    int t2 = ad.getInt();
                    debug("underline %d ", t2);
                    break;
                case 7:
                    int t6 = ad.getInt();
                    debug("strikethrough %d ", t6);
                    break;
                case 8:
                    int baseline = ad.getInt();
                    debug("baseline %d ", baseline);
                    break;
                case 9:
                    if (url != null)
                        err("Already saw url %s", url);
                    url = ad.getString();
                    debug("url %s ", url);
                    break;
                case 10:
                    if (color != null)
                        err("Already saw color %s", color.toString());
                    ArchivedObjectReader cnr = ad.getObject();
                    float red = 0, green = 0, blue = 0, alpha = 0;
                    ObjectData cnd = cnr.next();
                    assert cnd.index() == 1;
                    red = cnd.getFloat();
                    cnd = cnr.next();
                    assert cnd.index() == 2;
                    green = cnd.getFloat();
                    cnd = cnr.next();
                    assert cnd.index() == 3;
                    blue = cnd.getFloat();
                    cnd = cnr.next();
                    assert cnd.index() == 4;
                    alpha = cnd.getFloat();
                    cnd = cnr.next();
                    assert cnd == null;
                    color = new Color(red, green, blue, alpha);
                    debug("color %s ", color.toString());
                    break;
                case 11:        // XXX - don't know what this is yet
                    int t4 = ad.getInt();
                    debug("0x58=%d ", t4);
                    break;
                case 12:
                    // this is a UUID plus a string type
                    // the uuid indexes ZICCLOUDSYNCINGOBJECT/ZIDENTIFIER
                    if (uuid != null)
                        err("Already saw uuid %s", uuid);
                    ArchivedObjectReader inr = ad.getObject();
                    ObjectData ind = inr.next();
                    assert ind.index() == 1;
                    uuid = ind.getString();
                    ind = inr.next();
                    assert ind.index() == 2;
                    utype = ind.getString();
                    ind = inr.next();
                    assert ind == null;
                    debug("uuid %s type %s ", uuid, utype);
                    break;
                default:
                    err("Unexpected attribute data: %d%n%s",
                            ad.index(), bytesToHex(ad.getBytes()));
                    break aloop;
                }
            }
            debug("%n");

            Attribute a = new Attribute(len);
            // XXX - unify all the list styles?
            if (parastyle == ParagraphStyle.CHECKLIST)
                a.styles().add(new ChecklistStyle(checked, indent));
            else if (parastyle >= ParagraphStyle.LIST_START)
                a.styles().add(new ListStyle(parastyle, indent));
            else
                a.styles().add(new ParagraphStyle(parastyle));
            if (uuid != null)
                a.styles().add(new UuidStyle(uuid, utype));
            if (url != null)
                a.styles().add(new UrlStyle(url));
            if (fname != null || fsize != DEFAULT_FONT_SIZE)
                a.styles().add(new FontStyle(fname, fsize));
            if (style != 0)
                a.styles().add(new TextStyle(style));
            if (color != null)
                a.styles().add(new ColorStyle(color));
            attributes.add(a);
        }
        if (totalLen != text.length())
            err("text len %d, attr len %d", text.length(), totalLen);

        if (html)
            return getHtmlText(text, attributes);
        else if (markdown)
            return getMarkdownText(text, attributes);
        else
            return getMarkedText(text, attributes);
    }

    /**
     * Given the plain text and list of Attributes, return a string
     * with markers showing where each attribute applies.
     */
    private static String getMarkedText(String text,
                                            List<Attribute> attributes) {
        StringBuilder mtext = new StringBuilder();
        int anum = 1;
        int tpos = 0;
        for (Attribute a : attributes) {
            mtext.append('<').append(anum).append('>');
            mtext.append(text.substring(tpos, tpos + a.length()));
            mtext.append("</").append(anum).append('>');
            tpos += a.length();
            anum++;
        }
        return mtext.toString();
    }

    /**
     * Given the plain text and list of Attributes, return a string
     * with HTML markup.
     *
     * XXX - could detect title/header plus font size to change "h" level.
     * XXX - many more cases to handle below.
     */
    private static String getHtmlText(String text,
                                            List<Attribute> attributes) {
        StringBuilder mtext = new StringBuilder();
        int tpos = 0;
        ParagraphStyle curps = new ParagraphStyle(ParagraphStyle.NONE);
        for (Attribute a : attributes) {
            String atext = text.substring(tpos, tpos + a.length());

            /*
             * Process each "line" of the text.
             */
            int starti = 0;
            String prevline = "";
            while (starti >= 0) {
                String line;
                int nl = atext.indexOf('\n', starti);
                if (nl >= 0) {
                    nl++;
                    line = atext.substring(starti, nl);
                    starti = nl < atext.length() ? nl : -1;
                } else {
                    line = atext.substring(starti);
                    starti = -1;
                }

                /*
                 * If the text ends with a newline, move it out.
                 */
                boolean needNewline = false;
                if (line.endsWith("\n")) {
                    line = line.substring(0, line.length() - 1);
                    needNewline = true;
                }

                /*
                 * For each style, add the opening html and save the
                 * closing html.
                 */
                List<String> close = new ArrayList<String>();       // a stack
                for (Style s : a.styles()) {
                    if (s instanceof ParagraphStyle) {
                        ParagraphStyle ps = (ParagraphStyle)s;
                        boolean psHandled = false;
                        if (ps instanceof ListStyle &&
                                curps instanceof ListStyle) {
                            // nested lists
                            ListStyle psl = (ListStyle)ps;
                            ListStyle curpsl = (ListStyle)curps;
                            if (psl.indent > curpsl.indent) {
                                // start new indented
                                paraStart(ps, mtext);
                                psHandled = true;
                            } else if (psl.indent < curpsl.indent) {
                                // end previous indented
                                paraEnd(curps, mtext);
                                psHandled = true;
                            }
                        }
                        if (!psHandled && !ps.equals(curps)) {
                            // terminate previous paragraph style
                            paraEnd(curps, mtext);

                            // start new paragraph style
                            paraStart(ps, mtext);
                        }

                        if (endsWithNewline(mtext)) {
                            switch (ps.style) {
                            case ParagraphStyle.NONE:
                                // if line starts with whitespace, replace
                                // each whitespace char with "&nbsp;"
                                if (line.startsWith(" ") ||
                                        line.startsWith("\t")) {
                                    int i;
                                    for (i = 0; i < line.length(); i++) {
                                        char c = line.charAt(i);
                                        if (c != ' ' && c != '\t')
                                            break;
                                        if (c == '\t')
                                            mtext.append(
                            "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
                                        else
                                            mtext.append("&nbsp;");
                                    }
                                    line = line.substring(i);
                                }
                                break;
                            case ParagraphStyle.BULLET:
                            case ParagraphStyle.DASHED:
                            case ParagraphStyle.NUMBERED:
                                mtext.append("<li>");
                                break;
                            case ParagraphStyle.CHECKLIST:
                                ChecklistStyle cs = (ChecklistStyle)ps;
                                if (cs.checked)
                                    mtext.append("<li><input checked=\"\" " +
                                        "disabled=\"\" type=\"checkbox\">");
                                else
                                    mtext.append("<li><input disabled=\"\" " +
                                                "type=\"checkbox\">");
                                break;
                            }
                        }

                        curps = ps;
                    } else if (s instanceof UuidStyle) {
                        UuidStyle us = (UuidStyle)s;
                        mtext.append(String.format("<INSERT UUID %s, TYPE %s>",
                                                    us.uuid, us.type));
                        close.add("</INSERT>");
                    } else if (s instanceof UrlStyle) {
                        UrlStyle us = (UrlStyle)s;
                        mtext.append("<a href=\"").append(us.url).append("\">");
                        close.add("</a>");
                    } else if (s instanceof FontStyle) {
                        FontStyle fs = (FontStyle)s;
                        // XXX - font name ignored for now
                        // XXX - is this the right way to handle non-integer sizes?
                        mtext.append("<font size=\"").append(fontSize(fs.size)).
                                append("\">");
                        close.add("</font>");
                    } else if (s instanceof TextStyle) {
                        TextStyle ts = (TextStyle)s;
                        if ((ts.style & TextStyle.BOLD) != 0) {
                            mtext.append("<b>");
                            close.add("</b>");
                        }
                        if ((ts.style & TextStyle.ITALIC) != 0) {
                            mtext.append("<i>");
                            close.add("</i>");
                        }
                    } else if (s instanceof ColorStyle) {
                        ColorStyle cs = (ColorStyle)s;
                        // XXX - now what?
                    } else {
                        mtext.append("<UNKNOWN>");
                        close.add("</UNKNOWN>");
                    }
                }

                // Add the text.
                mtext.append(htmlText(line));

                /*
                 * Add the closing elements, in reverse order.
                 */
                for (int i = close.size() - 1; i >= 0; i--)
                    mtext.append(close.get(i));

                if (needNewline) {
                    switch (curps.style) {
                    case ParagraphStyle.NONE:
                        mtext.append("\n<br/>\n");
                        break;
                    case ParagraphStyle.BULLET:
                    case ParagraphStyle.DASHED:
                    case ParagraphStyle.NUMBERED:
                    case ParagraphStyle.CHECKLIST:
                        mtext.append("</li>\n");
                        break;
                    }
                }
                prevline = line;
            }
            tpos += a.length();
        }

        // close any open element
        switch (curps.style) {
        case ParagraphStyle.NONE:
            if (mtext.length() > 0)
                mtext.append("</p>\n");
            break;
        case ParagraphStyle.TITLE:
            mtext.append("</h1>\n");
            break;
        case ParagraphStyle.HEADING:
            mtext.append("</h2>\n");
            break;
        case ParagraphStyle.MONO:
            mtext.append("</code>\n");
            break;
        case ParagraphStyle.BULLET:
        case ParagraphStyle.DASHED:
        case ParagraphStyle.CHECKLIST:
            // XXX - doesn't handle indent
            mtext.append("</ul>\n");
            break;
        case ParagraphStyle.NUMBERED:
            // XXX - doesn't handle indent
            mtext.append("</ol>\n");
            break;
        default:
        }

        return mtext.toString();
    }

    /**
     * Given the plain text and list of Attributes, return a string
     * with markdown markup.
     *
     * XXX - many more cases to handle below.
     */
    private static String getMarkdownText(String text,
                                            List<Attribute> attributes) {
        StringBuilder mtext = new StringBuilder();
        int tpos = 0;
        ParagraphStyle curps = new ParagraphStyle(ParagraphStyle.NONE);
        for (Attribute a : attributes) {
            String atext = text.substring(tpos, tpos + a.length());

            /*
             * Process each "line" of the text.
             */
            int starti = 0;
            String prevline = "";
            while (starti >= 0) {
                String line;
                int nl = atext.indexOf('\n', starti);
                if (nl >= 0) {
                    nl++;
                    line = atext.substring(starti, nl);
                    starti = nl < atext.length() ? nl : -1;
                } else {
                    line = atext.substring(starti);
                    starti = -1;
                }

                /*
                 * If the text ends with a newline, move it out.
                 */
                boolean needNewline = false;
                if (line.endsWith("\n")) {
                    line = line.substring(0, line.length() - 1);
                    needNewline = true;
                }

                /*
                 * For each style, add the opening markdown and save the
                 * closing markdown.
                 */
                List<String> close = new ArrayList<String>();       // a stack
                for (Style s : a.styles()) {
                    if (s instanceof ParagraphStyle) {
                        ParagraphStyle ps = (ParagraphStyle)s;
                        if (!ps.equals(curps)) {
                            // terminate previous paragraph style
                            // XXX - not for first paragraph
                            switch (curps.style) {
                            case ParagraphStyle.MONO:
                                mtext.append("```\n");
                                break;
                            case ParagraphStyle.TITLE:
                            case ParagraphStyle.HEADING:
                            case ParagraphStyle.BULLET:
                            case ParagraphStyle.DASHED:
                            case ParagraphStyle.NUMBERED:
                            case ParagraphStyle.CHECKLIST:
                            default:
                                if (!endsWithNewline(mtext))
                                    mtext.append("\n");
                            }

                            // start new paragraph style
                            switch (ps.style) {
                            case ParagraphStyle.NONE:
                                ensureBlankLine(mtext);
                                break;
                            case ParagraphStyle.TITLE:
                                mtext.append("# ");
                                break;
                            case ParagraphStyle.HEADING:
                                mtext.append("## ");
                                break;
                            case ParagraphStyle.MONO:
                                ensureBlankLine(mtext);
                                mtext.append("```\n");
                                break;
                            case ParagraphStyle.BULLET:
                            case ParagraphStyle.DASHED:
                            case ParagraphStyle.NUMBERED:
                            case ParagraphStyle.CHECKLIST:
                                break;
                            default:
                                // XXX - not handled yet
                                mtext.append(String.format("<div style=\"%d\">",
                                                            ps.style));
                                close.add("</div>");
                            }
                        }

                        if (endsWithNewline(mtext)) {
                            switch (ps.style) {
                            case ParagraphStyle.NONE:
                                // if previous line was also a plain line,
                                // need to add a hard line break
                                if (curps.style == ParagraphStyle.NONE &&
                                        line.length() > 0 &&
                                        prevline.length() > 0) {
                                    mtext.setLength(mtext.length() - 1);
                                    mtext.append("\\\n");
                                }
                                // if line starts with whitespace, replace
                                // each whitespace char with "&nbsp;"
                                if (line.startsWith(" ") ||
                                        line.startsWith("\t")) {
                                    int i;
                                    for (i = 0; i < line.length(); i++) {
                                        char c = line.charAt(i);
                                        if (c != ' ' && c != '\t')
                                            break;
                                        if (c == '\t')
                                            mtext.append(
                            "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
                                        else
                                            mtext.append("&nbsp;");
                                    }
                                    line = line.substring(i);
                                }
                                break;
                            case ParagraphStyle.BULLET:
                                addIndent(mtext, ((ListStyle)ps).indent);
                                mtext.append("* ");
                                break;
                            case ParagraphStyle.DASHED:
                                addIndent(mtext, ((ListStyle)ps).indent);
                                mtext.append("- ");
                                break;
                            case ParagraphStyle.NUMBERED:
                                addIndent(mtext, ((ListStyle)ps).indent);
                                // XXX - valid markdown, but ugly
                                mtext.append("1. ");
                                break;
                            case ParagraphStyle.CHECKLIST:
                                ChecklistStyle cs = (ChecklistStyle)ps;
                                addIndent(mtext, cs.indent);
                                if (cs.checked)
                                    mtext.append("- [x] ");
                                else
                                    mtext.append("- [ ] ");
                                break;
                            }
                        }

                        curps = ps;
                    } else if (s instanceof UuidStyle) {
                        UuidStyle us = (UuidStyle)s;
                        mtext.append(String.format("<INSERT UUID %s, TYPE %s>",
                                                    us.uuid, us.type));
                    } else if (s instanceof UrlStyle) {
                        UrlStyle us = (UrlStyle)s;
                        mtext.append("[");
                        close.add("](" + us.url + ")");
                    } else if (s instanceof FontStyle) {
                        FontStyle fs = (FontStyle)s;
                        // XXX - font name ignored for now
                        // XXX - is this the right way to handle non-integer sizes?
                        mtext.append("<font size=\"").append(fontSize(fs.size)).
                                append("\">");
                        close.add("</font>");
                    } else if (s instanceof TextStyle) {
                        TextStyle ts = (TextStyle)s;
                        if ((ts.style & TextStyle.BOLD) != 0) {
                            mtext.append("**");
                            close.add("**");
                        }
                        if ((ts.style & TextStyle.ITALIC) != 0) {
                            mtext.append("_");
                            close.add("_");
                        }
                    } else if (s instanceof ColorStyle) {
                        ColorStyle cs = (ColorStyle)s;
                        // XXX - now what?
                    } else {
                        mtext.append("<UNKNOWN>");
                        close.add("</UNKNOWN>");
                    }
                }

                // Add the text.
                mtext.append(markdownText(line));

                /*
                 * Add the closing elements, in reverse order.
                 */
                for (int i = close.size() - 1; i >= 0; i--)
                    mtext.append(close.get(i));
                if (needNewline)
                    mtext.append("\n");
                prevline = line;
            }
            tpos += a.length();
        }
        return mtext.toString();
    }

    /**
     * Convert the plain text to html.
     * For now, just replace all "," with the html equivalent to
     * prevent it from looking like an html tag, and replace all
     * the newlines with <br/>.
     * XXX - probably more quoting/escaping needs to be done here.
     */
    private static String htmlText(String text) {
        return text.replace("<", "&lt;").replace("\n", "<br/>\n");
    }

    /**
     * Start paragraph style.
     */
    private static void paraStart(ParagraphStyle ps, StringBuilder mtext) {
        // start new paragraph style
        switch (ps.style) {
        case ParagraphStyle.NONE:
            mtext.append("<p>\n");
            break;
        case ParagraphStyle.TITLE:
            mtext.append("<h1>\n");
            break;
        case ParagraphStyle.HEADING:
            mtext.append("<h2>\n");
            break;
        case ParagraphStyle.MONO:
            mtext.append("<code>\n");
            break;
        case ParagraphStyle.BULLET:
        case ParagraphStyle.DASHED:
        case ParagraphStyle.CHECKLIST:
            mtext.append("<ul>\n");
            break;
        case ParagraphStyle.NUMBERED:
            mtext.append("<ol>\n");
            break;
        default:
            // XXX - not handled yet
            mtext.append(String.format("<div style=\"%d\">", ps.style));
        }
    }

    /**
     * End paragraph style.
     */
    private static void paraEnd(ParagraphStyle ps, StringBuilder mtext) {
        switch (ps.style) {
        case ParagraphStyle.NONE:
            if (mtext.length() > 0)
                mtext.append("</p>\n");
            break;
        case ParagraphStyle.TITLE:
            mtext.append("</h1>\n");
            break;
        case ParagraphStyle.HEADING:
            mtext.append("</h2>\n");
            break;
        case ParagraphStyle.MONO:
            mtext.append("</code>\n");
            break;
        case ParagraphStyle.BULLET:
        case ParagraphStyle.DASHED:
        case ParagraphStyle.CHECKLIST:
            mtext.append("</ul>\n");
            break;
        case ParagraphStyle.NUMBERED:
            mtext.append("</ol>\n");
            break;
        default:
            mtext.append("</div>\n");
        }
    }

    /**
     * Convert the plain text to markdown.
     * Turn newlines into hard line breaks.
     * (Backslash at end of line only works if following
     * line is not empty.)
     * XXX - probably more quoting/escaping needs to be done here.
     */
    private static String markdownText(String text) {
        return text.replaceAll("\n([^\n])", "\\\\\n$1");
    }

    /**
     * Convert font size to a string.
     */
    private static String fontSize(float fs) {
        int intSize = (int)fs;
        if ((float)intSize == fs)
            return Integer.toString(intSize);
        else
            return Float.toString(fs);
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++)
            sb.append(String.format("%02x  ", ((int)bytes[i]) & 0xff));
        if (sb.length() > 2)
            sb.setLength(sb.length() - 2);
        return sb.toString();
    }

    private static boolean endsWithNewline(StringBuilder sb) {
        return sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n';
    }

    private static void ensureBlankLine(StringBuilder sb) {
        int len = sb.length();
        if (len >= 2) {
            if (sb.charAt(len - 1) != '\n')
                sb.append("\n\n");
            else if (sb.charAt(len - 2) != '\n')
                sb.append('\n');
        } else if (len == 1 && sb.charAt(0) != '\n') {
            sb.append("\n\n");
        }
    }

    private static void addIndent(StringBuilder sb, int indent) {
        while (indent-- > 0)
            sb.append("  ");
    }

    private static void debug(String s, Object... args) {
        if (debug)
            System.out.printf(s, args);
    }

    private static void err(String s, Object... args) {
        System.out.printf("ERR: " + s, args);
        System.out.println();
    }
}
