package org.openstreetmap.josm.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.Collections;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Utils;

public class CopyCoordinatesAction extends JosmAction {

    public CopyCoordinatesAction() {
        super(tr("Copy Coordinates"), null,
                tr("Copy coordinates of selected nodes to clipboard."),
                Shortcut.registerShortcut("copy:coordinates", tr("Edit: {0}", tr("Copy Coordinates")),
                KeyEvent.VK_C, Shortcut.CTRL_SHIFT),
                false);
        putValue("toolbar", "copy/coordinates");
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        StringBuilder s = new StringBuilder();
        for (Node n : getSelectedNodes()) {
            s.append(n.getCoor().lat());
            s.append(", ");
            s.append(n.getCoor().lon());
            s.append("\n");
        }
        Utils.copyToClipboard(s.toString().trim());
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(!getSelectedNodes().isEmpty());
    }

    @Override
    protected void updateEnabledState(Collection<? extends OsmPrimitive> selection) {
        updateEnabledState();
    }

    private Collection<Node> getSelectedNodes() {
        if (getCurrentDataSet() == null || getCurrentDataSet().getSelected() == null) {
            return Collections.emptyList();
        } else {
            return Utils.filteredCollection(getCurrentDataSet().getSelected(), Node.class);
        }
    }
}
