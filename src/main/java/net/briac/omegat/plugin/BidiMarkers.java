/**************************************************************************
Better Bidi Markers
Copyright (C) 2020 Briac Pilpre

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
**************************************************************************/
package net.briac.omegat.plugin;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.text.Highlighter.HighlightPainter;
import javax.swing.text.JTextComponent;

import org.omegat.core.Core;
import org.omegat.core.data.SourceTextEntry;
import org.omegat.gui.editor.UnderlineFactory.Underliner;
import org.omegat.gui.editor.mark.AbstractMarker;
import org.omegat.gui.editor.mark.Mark;
import org.omegat.util.StringUtil;
import org.omegat.util.gui.Styles;

public class BidiMarkers extends AbstractMarker {
    public static final String PLUGIN_NAME = BidiMarkers.class.getPackage().getImplementationTitle();
    public static final String PLUGIN_VERSION = BidiMarkers.class.getPackage().getImplementationVersion();
    private static final Logger LOGGER = Logger.getLogger(BidiMarkers.class.getName());

    private static final List<Mark> EMPTY_LIST = Collections.emptyList();

    private static final int LRM = 0x200e;
    private static final int RLM = 0x200f;
    private static final int LRE = 0x202a;
    private static final int RLE = 0x202b;
    private static final int PDF = 0x202c;

    static final HighlightPainter LRE_BIDI_PAINTER = new BidiUnderliner(LRE,
            Styles.EditorColor.COLOR_BIDIMARKERS.getColor());
    static final HighlightPainter RLE_BIDI_PAINTER = new BidiUnderliner(RLE,
            Styles.EditorColor.COLOR_BIDIMARKERS.getColor());
    static final HighlightPainter LRM_BIDI_PAINTER = new BidiUnderliner(LRM,
            Styles.EditorColor.COLOR_BIDIMARKERS.getColor());
    static final HighlightPainter RLM_BIDI_PAINTER = new BidiUnderliner(RLM,
            Styles.EditorColor.COLOR_BIDIMARKERS.getColor());

    static {
        Core.registerMarkerClass(BidiMarkers.class);
    }

    public static void loadPlugins() {
        LOGGER.info("Loading " + PLUGIN_NAME + " v." + PLUGIN_VERSION + "...");
    }

    public static void unloadPlugins() {
        /* empty */
    }

    public BidiMarkers() throws Exception {
        super();
    }

    @Override
    public List<Mark> getMarksForEntry(SourceTextEntry ste, String sourceText, String text, boolean isActive)
            throws Exception {
        if (!isActive || text == null || text.trim().isEmpty()) {
            return EMPTY_LIST;
        }

        text = StringUtil.normalizeUnicode(text);
        List<Mark> marks = new ArrayList<>();

        int startPos = -1;
        int markCodePoint = -1;
        for (int i = 0, cp; i < text.length(); i += Character.charCount(cp)) {
            cp = text.codePointAt(i);

            if (!(cp == LRE || cp == RLE || cp == LRM || cp == RLM || cp == PDF)) {
                continue;
            }

            LOGGER.finest("Mark " + bidiName(cp) + " found at pos " + i + ".");

            if (cp == PDF && startPos != -1) {
                Mark mark = new Mark(Mark.ENTRY_PART.TRANSLATION, startPos, i);
                switch (markCodePoint) {
                case LRE:
                    mark.painter = LRE_BIDI_PAINTER;
                    break;
                case RLE:
                    mark.painter = RLE_BIDI_PAINTER;
                    break;
                }
                marks.add(mark);
                LOGGER.finest("Add mark for " + bidiName(markCodePoint) + " pos " + startPos + ":" + i + ".");

                startPos = -1;
                markCodePoint = -1;
            } else if (cp == LRM || cp == RLM) {
                Mark mark = new Mark(Mark.ENTRY_PART.TRANSLATION, i, i);
                switch (cp) {
                case LRM:
                    mark.painter = LRM_BIDI_PAINTER;
                    break;
                case RLM:
                    mark.painter = RLM_BIDI_PAINTER;
                    break;
                }
                marks.add(mark);
                LOGGER.finest("Add mark for " + bidiName(cp) + " pos " + i + ".");
            } else {
                markCodePoint = cp;
                startPos = i;
            }
        }
        
        if (startPos != -1) {
            LOGGER.finest("Lone mark " + bidiName(markCodePoint) + " found at pos " + startPos + ".");
            Mark mark = new Mark(Mark.ENTRY_PART.TRANSLATION, startPos, startPos);
            mark.painter = markCodePoint == LRM ? LRM_BIDI_PAINTER : RLM_BIDI_PAINTER;
            marks.add(mark);
        }

        return marks;
    }

    @Override
    protected boolean isEnabled() {
        return true; // Core.getEditor().getSettings().isMarkBidi();
    }

    private static final class BidiUnderliner extends Underliner {
        protected final Color color;
        protected final int bidi;

        // The marker should be positioned slightly above the text
        private static final int HEIGHT_OFFSET = -3;
        
        // Height of the descending lines
        private static final int MARKER_HEIGHT = 6;

        private static final float STROKE_WIDTH = 1f;

        private static final BasicStroke BIDI_STROKE = new BasicStroke(STROKE_WIDTH);

        public BidiUnderliner(int b, Color c) {
            bidi = b;
            color = c;
        }

        @Override
        protected void paint(Graphics g, Rectangle rect, JTextComponent c) {
            LOGGER.finest("Paint " + bidiName(bidi) + " " + rect.x + " -> " + (rect.x + rect.width));
            g.setColor(color);

            int dir = bidi == LRE || bidi == LRM ? -1 : 1;

            int y = rect.y + HEIGHT_OFFSET;
            int x1 = rect.x;
            int x2 = rect.x + rect.width;

            Stroke oldStroke = ((Graphics2D) g).getStroke();
            ((Graphics2D) g).setStroke(BIDI_STROKE);

            Polygon p = new Polygon();

            // Draw starting bidi mark
            switch (bidi) {

            case RLM:
            case LRM:
                p.addPoint(x1, y + MARKER_HEIGHT);
                p.addPoint(x1, y);
                p.addPoint(x1 - dir * MARKER_HEIGHT, y);
                p.addPoint(x1, y + MARKER_HEIGHT);
                g.fillPolygon(p);
                g.drawPolygon(p);
                break;

            case RLE:
                p.addPoint(x2 + 1, y + MARKER_HEIGHT);
                p.addPoint(x2 + 1, y);
                p.addPoint(x2 + 1 - dir * MARKER_HEIGHT, y);
                p.addPoint(x2 + 1, y + MARKER_HEIGHT);
                g.drawPolygon(p);
                
                // Draw PDF Mark
                g.drawLine(x1, y, x1, y + MARKER_HEIGHT);

                // Line
                g.drawLine(x1, y, x2+1, y);
                break;

            case LRE:
                p.addPoint(x1 + 1, y + MARKER_HEIGHT);
                p.addPoint(x1 + 1, y);
                p.addPoint(x1 + 1 - dir * MARKER_HEIGHT, y);
                p.addPoint(x1 + 1, y + MARKER_HEIGHT);
                g.drawPolygon(p);
                
                // Draw PDF Mark
                g.drawLine(x2, y, x2, y + MARKER_HEIGHT);

                // Line
                g.drawLine(x1 + 1 , y, x2, y);
                break;

            default:
                break;
            }

            ((Graphics2D) g).setStroke(oldStroke);
        }
    }
    
    // For debug only
    private static String bidiName(int i) {
        switch (i) {
        case LRE:
            return "LRE";
        case LRM:
            return "LRM";
        case RLE:
            return "LRE";
        case RLM:
            return "RLM";
        case PDF:
            return "PDF";
        default:
            return "???";
        }
    }
}
