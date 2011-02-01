// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.xml;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmUtils;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.mappaint.Cascade;
import org.openstreetmap.josm.gui.mappaint.MultiCascade;
import org.openstreetmap.josm.gui.mappaint.Range;
import org.openstreetmap.josm.gui.preferences.SourceEntry;
import org.openstreetmap.josm.tools.Utils;

public class XmlStyleSource extends SourceEntry {

    public final HashMap<String, IconPrototype> icons = new HashMap<String, IconPrototype>();
    public final HashMap<String, LinePrototype> lines = new HashMap<String, LinePrototype>();
    public final HashMap<String, LinemodPrototype> modifiers = new HashMap<String, LinemodPrototype>();
    public final HashMap<String, AreaPrototype> areas = new HashMap<String, AreaPrototype>();
    public final LinkedList<IconPrototype> iconsList = new LinkedList<IconPrototype>();
    public final LinkedList<LinePrototype> linesList = new LinkedList<LinePrototype>();
    public final LinkedList<LinemodPrototype> modifiersList = new LinkedList<LinemodPrototype>();
    public final LinkedList<AreaPrototype> areasList = new LinkedList<AreaPrototype>();

    public boolean hasError = false;
    public File zipIcons;

    public XmlStyleSource(String url, String name, String shortdescription) {
        super(url, name, shortdescription, true);
    }

    public XmlStyleSource(SourceEntry entry) {
        super(entry.url, entry.name, entry.shortdescription, entry.active);
    }

    private static class WayPrototypesRecord {
        public LinePrototype line;
        public List<LinemodPrototype> linemods;
        public AreaPrototype area;
    }

    private <T extends Prototype> T update(T current, T candidate, Double scale, MultiCascade mc) {
        return requiresUpdate(current, candidate, scale, mc) ? candidate : current;
    }

    /**
     * checks whether a certain match is better than the current match
     * @param current can be null
     * @param candidate the new Prototype that could be used instead
     * @param scale ignored if null, otherwise checks if scale is within the range of candidate
     * @param mc side effect: update the valid region for the current MultiCascade
     */
    private boolean requiresUpdate(Prototype current, Prototype candidate, Double scale, MultiCascade mc) {
        if (current == null || candidate.priority >= current.priority) {
            if (scale == null)
                return true;

            if (candidate.range.contains(scale)) {
                mc.range = Range.cut(mc.range, candidate.range);
                return true;
            } else {
                mc.range = mc.range.reduceAround(scale, candidate.range);
                return false;
            }
        }
        return false;
    }

    private IconPrototype getNode(OsmPrimitive primitive, Double scale, MultiCascade mc) {
        IconPrototype icon = null;
        for (String key : primitive.keySet()) {
            String val = primitive.get(key);
            IconPrototype p;
            if ((p = icons.get("n" + key + "=" + val)) != null) {
                icon = update(icon, p, scale, mc);
            }
            if ((p = icons.get("b" + key + "=" + OsmUtils.getNamedOsmBoolean(val))) != null) {
                icon = update(icon, p, scale, mc);
            }
            if ((p = icons.get("x" + key)) != null) {
                icon = update(icon, p, scale, mc);
            }
        }
        for (IconPrototype s : iconsList) {
            if (s.check(primitive))
            {
                icon = update(icon, s, scale, mc);
            }
        }
        return icon;
    }

    /**
     * @param closed The primitive is a closed way or we pretend it is closed.
     *  This is useful for multipolygon relations and outer ways of untagged
     *  multipolygon relations.
     */
    private void get(OsmPrimitive primitive, boolean closed, WayPrototypesRecord p, Double scale, MultiCascade mc) {
        String lineIdx = null;
        HashMap<String, LinemodPrototype> overlayMap = new HashMap<String, LinemodPrototype>();
        for (String key : primitive.keySet()) {
            String val = primitive.get(key);
            AreaPrototype styleArea;
            LinePrototype styleLine;
            LinemodPrototype styleLinemod;
            String idx = "n" + key + "=" + val;
            if ((styleArea = areas.get(idx)) != null && (closed || !styleArea.closed)) {
                p.area = update(p.area, styleArea, scale, mc);
            }
            if ((styleLine = lines.get(idx)) != null) {
                if (requiresUpdate(p.line, styleLine, scale, mc)) {
                    p.line = styleLine;
                    lineIdx = idx;
                }
            }
            if ((styleLinemod = modifiers.get(idx)) != null) {
                if (requiresUpdate(null, styleLinemod, scale, mc)) {
                    overlayMap.put(idx, styleLinemod);
                }
            }
            idx = "b" + key + "=" + OsmUtils.getNamedOsmBoolean(val);
            if ((styleArea = areas.get(idx)) != null && (closed || !styleArea.closed)) {
                p.area = update(p.area, styleArea, scale, mc);
            }
            if ((styleLine = lines.get(idx)) != null) {
                if (requiresUpdate(p.line, styleLine, scale, mc)) {
                    p.line = styleLine;
                    lineIdx = idx;
                }
            }
            if ((styleLinemod = modifiers.get(idx)) != null) {
                if (requiresUpdate(null, styleLinemod, scale, mc)) {
                    overlayMap.put(idx, styleLinemod);
                }
            }
            idx = "x" + key;
            if ((styleArea = areas.get(idx)) != null && (closed || !styleArea.closed)) {
                p.area = update(p.area, styleArea, scale, mc);
            }
            if ((styleLine = lines.get(idx)) != null) {
                if (requiresUpdate(p.line, styleLine, scale, mc)) {
                    p.line = styleLine;
                    lineIdx = idx;
                }
            }
            if ((styleLinemod = modifiers.get(idx)) != null) {
                if (requiresUpdate(null, styleLinemod, scale, mc)) {
                    overlayMap.put(idx, styleLinemod);
                }
            }
        }
        for (AreaPrototype s : areasList) {
            if ((closed || !s.closed) && s.check(primitive)) {
                p.area = update(p.area, s, scale, mc);
            }
        }
        for (LinePrototype s : linesList) {
            if (s.check(primitive)) {
                p.line = update(p.line, s, scale, mc);
            }
        }
        for (LinemodPrototype s : modifiersList) {
            if (s.check(primitive)) {
                if (requiresUpdate(null, s, scale, mc)) {
                    overlayMap.put(s.getCode(), s);
                }
            }
        }
        overlayMap.remove(lineIdx); // do not use overlay if linestyle is from the same rule (example: railway=tram)
        if (!overlayMap.isEmpty()) {
            List<LinemodPrototype> tmp = new LinkedList<LinemodPrototype>();
            if (p.linemods != null) {
                tmp.addAll(p.linemods);
            }
            tmp.addAll(overlayMap.values());
            Collections.sort(tmp);
            p.linemods = tmp;
        }
    }

    public void add(XmlCondition c, Collection<XmlCondition> conditions, Prototype prot) {
         if(conditions != null)
         {
            prot.conditions = conditions;
            if (prot instanceof IconPrototype) {
                iconsList.add((IconPrototype) prot);
            } else if (prot instanceof LinemodPrototype) {
                modifiersList.add((LinemodPrototype) prot);
            } else if (prot instanceof LinePrototype) {
                linesList.add((LinePrototype) prot);
            } else if (prot instanceof AreaPrototype) {
                areasList.add((AreaPrototype) prot);
            } else
                throw new RuntimeException();
         }
         else {
             String key = c.getKey();
            prot.code = key;
            if (prot instanceof IconPrototype) {
                icons.put(key, (IconPrototype) prot);
            } else if (prot instanceof LinemodPrototype) {
               modifiers.put(key, (LinemodPrototype) prot);
            } else if (prot instanceof LinePrototype) {
                lines.put(key, (LinePrototype) prot);
            } else if (prot instanceof AreaPrototype) {
                areas.put(key, (AreaPrototype) prot);
            } else
                throw new RuntimeException();
         }
     }

    public void apply(MultiCascade mc, OsmPrimitive osm, double scale, OsmPrimitive multipolyOuterWay, boolean pretendWayIsClosed) {
        Cascade def = mc.getCascade("default");
        boolean useMinMaxScale = Main.pref.getBoolean("mappaint.zoomLevelDisplay", false);

        if (osm instanceof Node || (osm instanceof Relation && "restriction".equals(osm.get("type")))) {
            IconPrototype icon = getNode(osm, (useMinMaxScale ? scale : null), mc);
            if (icon != null) {
                def.put("icon-image", icon.icon);
                if (osm instanceof Node) {
                    if (icon.annotate != null) {
                        if (icon.annotate) {
                            def.put("text", "yes");
                        } else {
                            def.remove("text");
                        }
                    }
                }
            }
        } else if (osm instanceof Way || (osm instanceof Relation && "multipolygon".equals(osm.get("type")))) {
            WayPrototypesRecord p = new WayPrototypesRecord();
            get(osm, pretendWayIsClosed || !(osm instanceof Way) || ((Way) osm).isClosed(), p, (useMinMaxScale ? scale : null), mc);
            if (p.line != null) {
                def.put("width", new Float(p.line.getWidth()));
                def.putOrClear("real-width", p.line.realWidth != null ? new Float(p.line.realWidth) : null);
                def.putOrClear("color", p.line.color);
                def.putOrClear("dashes", p.line.getDashed());
                def.putOrClear("dashes-background-color", p.line.dashedColor);
            }
            Float refWidth = def.get("width", null, Float.class);
            if (refWidth != null && p.linemods != null) {
                int numOver = 0, numUnder = 0;

                while (mc.containsKey(String.format("over_%d", ++numOver))) {}
                while (mc.containsKey(String.format("under_%d", ++numUnder))) {}

                for (LinemodPrototype mod : p.linemods) {
                    Cascade c;
                    if (mod.over) {
                        c = mc.getCascade(String.format("over_%d", numOver));
                        c.put("object-z-index", new Float(numOver));
                        ++numOver;
                    } else {
                        c = mc.getCascade(String.format("under_%d", numUnder));
                        c.put("object-z-index", new Float(-numUnder));
                        ++numUnder;
                    }
                    c.put("width", new Float(mod.getWidth(refWidth)));
                    c.putOrClear("color", mod.color);
                    c.putOrClear("dashes", mod.getDashed());
                    c.putOrClear("dashes-background-color", mod.dashedColor);
                }
            }
            if (multipolyOuterWay != null) {
                WayPrototypesRecord p2 = new WayPrototypesRecord();
                get(multipolyOuterWay, true, p2, (useMinMaxScale ? scale : null), mc);
                if (Utils.equal(p.area, p2.area)) {
                    p.area = null;
                }
            }
            if (p.area != null) {
                def.putOrClear("fill-color", p.area.color);
                def.remove("fill-image");
            }
        }
    }

}