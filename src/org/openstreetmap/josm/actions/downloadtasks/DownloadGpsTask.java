// License: GPL. Copyright 2007 by Immanuel Scholz and others
package org.openstreetmap.josm.actions.downloadtasks;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.Bounds.ParseMethod;
import org.openstreetmap.josm.data.gpx.GpxData;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressTaskId;
import org.openstreetmap.josm.gui.progress.ProgressTaskIds;
import org.openstreetmap.josm.io.BoundingBoxDownloader;
import org.openstreetmap.josm.io.OsmServerLocationReader;
import org.openstreetmap.josm.io.OsmServerReader;
import org.openstreetmap.josm.io.OsmTransferException;
import org.xml.sax.SAXException;

public class DownloadGpsTask extends AbstractDownloadTask {

    private DownloadTask downloadTask;

    private static final String PATTERN_TRACE_ID = "http://.*openstreetmap.org/trace/\\p{Digit}+/data";

    private static final String PATTERN_TRACKPOINTS_BBOX = "http://.*/api/0.6/trackpoints\\?bbox=.*,.*,.*,.*";

    private static final String PATTERN_EXTERNAL_GPX_SCRIPT = "http://.*exportgpx.*";
    private static final String PATTERN_EXTERNAL_GPX_FILE = "http://.*/(.*\\.gpx)";

    protected String newLayerName = null;

    public Future<?> download(boolean newLayer, Bounds downloadArea, ProgressMonitor progressMonitor) {
        downloadTask = new DownloadTask(newLayer,
                new BoundingBoxDownloader(downloadArea), progressMonitor);
        // We need submit instead of execute so we can wait for it to finish and get the error
        // message if necessary. If no one calls getErrorMessage() it just behaves like execute.
        return Main.worker.submit(downloadTask);
    }

    public Future<?> loadUrl(boolean newLayer, String url, ProgressMonitor progressMonitor) {
        if (url != null && (url.matches(PATTERN_TRACE_ID) || url.matches(PATTERN_EXTERNAL_GPX_SCRIPT) || url.matches(PATTERN_EXTERNAL_GPX_FILE))) {
            downloadTask = new DownloadTask(newLayer,
                    new OsmServerLocationReader(url), progressMonitor);
            // Extract .gpx filename from URL to set the new layer name
            Matcher matcher = Pattern.compile(PATTERN_EXTERNAL_GPX_FILE).matcher(url);
            newLayerName = matcher.matches() ? matcher.group(1) : null;
            // We need submit instead of execute so we can wait for it to finish and get the error
            // message if necessary. If no one calls getErrorMessage() it just behaves like execute.
            return Main.worker.submit(downloadTask);

        } else if (url != null && url.matches(PATTERN_TRACKPOINTS_BBOX)) {
            String[] table = url.split("\\?|=|&");
            for (int i = 0; i<table.length; i++) {
                if (table[i].equals("bbox") && i<table.length-1 )
                    return download(newLayer, new Bounds(table[i+1], ",", ParseMethod.LEFT_BOTTOM_RIGHT_TOP), progressMonitor);
            }
        }
        return null;
    }

    /* (non-Javadoc)
     * @see org.openstreetmap.josm.actions.downloadtasks.DownloadTask#acceptsUrl(java.lang.String)
     */
    @Override
    public boolean acceptsUrl(String url) {
        return url != null && (url.matches(PATTERN_TRACE_ID) || url.matches(PATTERN_TRACKPOINTS_BBOX)
                || url.matches(PATTERN_EXTERNAL_GPX_SCRIPT) || url.matches(PATTERN_EXTERNAL_GPX_FILE));
    }

    public void cancel() {
        if (downloadTask != null) {
            downloadTask.cancel();
        }
    }

    class DownloadTask extends PleaseWaitRunnable {
        private OsmServerReader reader;
        private GpxData rawData;
        private final boolean newLayer;

        public DownloadTask(boolean newLayer, OsmServerReader reader, ProgressMonitor progressMonitor) {
            super(tr("Downloading GPS data"));
            this.reader = reader;
            this.newLayer = newLayer;
        }

        @Override public void realRun() throws IOException, SAXException, OsmTransferException {
            try {
                if (isCanceled())
                    return;
                rawData = reader.parseRawGps(progressMonitor.createSubTaskMonitor(ProgressMonitor.ALL_TICKS, false));
            } catch(Exception e) {
                if (isCanceled())
                    return;
                if (e instanceof OsmTransferException) {
                    rememberException(e);
                } else {
                    rememberException(new OsmTransferException(e));
                }
            }
        }

        @Override protected void finish() {
            if (isCanceled() || isFailed())
                return;
            if (rawData == null)
                return;
            String name = newLayerName != null ? newLayerName : tr("Downloaded GPX Data");
            GpxLayer layer = new GpxLayer(rawData, name);
            Layer x = findMergeLayer();
            if (newLayer || x == null) {
                Main.main.addLayer(layer);
            } else {
                x.mergeFrom(layer);
                Main.map.repaint();
            }
        }

        private Layer findMergeLayer() {
            boolean merge = Main.pref.getBoolean("download.gps.mergeWithLocal", false);
            if (!Main.isDisplayingMapView())
                return null;
            Layer active = Main.map.mapView.getActiveLayer();
            if (active != null && active instanceof GpxLayer && (merge || ((GpxLayer)active).data.fromServer))
                return active;
            for (Layer l : Main.map.mapView.getAllLayers()) {
                if (l instanceof GpxLayer &&  (merge || ((GpxLayer)l).data.fromServer))
                    return l;
            }
            return null;
        }

        @Override protected void cancel() {
            setCanceled(true);
            if (reader != null) {
                reader.cancel();
            }
        }

        @Override
        public ProgressTaskId canRunInBackground() {
            return ProgressTaskIds.DOWNLOAD_GPS;
        }
    }
}
