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

    private static final int LRE = 0x202a;
    private static final int RLE = 0x202b;
    private static final int PDF = 0x202c;

    static final HighlightPainter BIDI_PAINTER = new BidiUnderliner(true,
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
        for (int i = 0, cp; i < text.length(); i += Character.charCount(cp)) {
            cp = text.codePointAt(i);

            if (cp == LRE || cp == RLE) {
                startPos = i;
            } else if (cp == PDF && startPos != -1) {
                Mark mark = new Mark(Mark.ENTRY_PART.TRANSLATION, startPos, i);
                mark.painter = BIDI_PAINTER;
                marks.add(mark);

                startPos = -1;
            }
        }

        return marks;
    }

    @Override
    protected boolean isEnabled() {
        return true; // Core.getEditor().getSettings().isMarkBidi();
    }

    private static final class BidiUnderliner extends Underliner {
        protected final Color color;
        private boolean lre;
        private static final BasicStroke BIDI_STROKE = new BasicStroke(2);
        private static final int MARKER_SIZE = 6;
        
        private int heightOffset = -3;
        private int widthOffset = -3;

        public BidiUnderliner(boolean b, Color c) {
            lre = b;
            color = c;
        }

        @Override
        protected void paint(Graphics g, Rectangle rect, JTextComponent c) {
            g.setColor(color);

            int dir = lre ? -1 : 1;
            int halfHeight = rect.height / 2;

            int y = rect.y + heightOffset ;
            int x1 = rect.x + widthOffset;
            int x2 = rect.x + rect.width;

            Stroke oldStroke = ((Graphics2D) g).getStroke();
            ((Graphics2D) g).setStroke(BIDI_STROKE);

            Polygon p = new Polygon();
            // Draw start bidi char
            p.addPoint(x1, y + halfHeight);
            p.addPoint(x1, y);
            p.addPoint(x1 - dir * MARKER_SIZE * 2, y);
            p.addPoint(x1, y);
            g.drawPolygon(p);

            // Draw PDF
            g.drawArc(x2, y, MARKER_SIZE, MARKER_SIZE, 0, 360);

            // line
            //g.drawLine(x1, y, x2, y);
            ((Graphics2D) g).setStroke(oldStroke);
        }
    }
}
