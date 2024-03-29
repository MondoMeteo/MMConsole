/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2006-2008, Open Source Geospatial Foundation (OSGeo)
 *
 *    This file is hereby placed into the Public Domain. This means anyone is
 *    free to do whatever they wish with this file. Use it well and enjoy!
 */
// docs start source
package org.mondometeo.console;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import org.geotools.coverage.GridSampleDimension;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridCoverage2DReader;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.Parameter;
import org.geotools.data.ows.Layer;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.wms.WebMapServer;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.map.FeatureLayer;
import org.geotools.map.GridReaderLayer;

import org.geotools.map.MapContent;
import org.geotools.map.WMSLayer;
import org.geotools.ows.ServiceException;
import org.geotools.styling.ChannelSelection;
import org.geotools.styling.ContrastEnhancement;
import org.geotools.styling.RasterSymbolizer;
import org.geotools.styling.SLD;
import org.geotools.styling.SelectedChannelType;
import org.geotools.styling.Style;
import org.geotools.styling.StyleFactory;
import org.geotools.swing.JMapFrame;
import org.geotools.swing.action.SafeAction;
import org.geotools.swing.data.JParameterListWizard;
import org.geotools.swing.wizard.JWizard;
import org.geotools.swing.wms.WMSChooser;
import org.geotools.swing.wms.WMSLayerChooser;
import org.geotools.util.KVP;
import org.opengis.filter.FilterFactory2;
import org.opengis.style.ContrastMethod;

public class ImageLab {

    private StyleFactory sf = CommonFactoryFinder.getStyleFactory();
    private FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
    private JMapFrame frame;
    private MapContent map;

    public static void main(String[] args) throws Exception {
        ImageLab me = new ImageLab();
        me.showMe();
    }

    public void showMe() throws Exception {
        prepareTheMap();
        
        frame = new JMapFrame(map);
        frame.setSize(800, 600);
        frame.enableStatusBar(true);
        frame.enableToolBar(true);

        JMenuBar menuBar = new JMenuBar();
        frame.setJMenuBar(menuBar);
        JMenu menu = new JMenu("Raster");
        menuBar.add(menu);
        menu.add(new SafeAction("Wizard Shape Layers") {
            public void action(ActionEvent e) throws Throwable {
                getLayersFromSheapFile();
            }
        });

        menu.add(new SafeAction("Wizard Image Layers") {
            public void action(ActionEvent e) throws Throwable {
                getLayersFromImageFile();
            }
        });
//
//        menu.add(new SafeAction("Grayscale display") {
//            public void action(ActionEvent e) throws Throwable {
//                Style style = createGreyscaleStyle();
//                if (style != null) {
//                    ((StyleLayer) map.layers().get(0)).setStyle(style);
//                    frame.repaint();
//                }
//            }
//        });
//
//        menu.add(new SafeAction("RGB display") {
//            public void action(ActionEvent e) throws Throwable {
//                Style style = createRGBStyle();
//                if (style != null) {
//                    ((StyleLayer) map.layers().get(0)).setStyle(style);
//                    frame.repaint();
//                }
//            }
//        });
        // Finally display the map frame.
        // When it is closed the app will exit.
        frame.setVisible(true);
    }

    // docs end display layers
    // docs start create greyscale style
    /**
     * Create a Style to display a selected band of the GeoTIFF image as a
     * greyscale layer
     *
     * @return a new Style instance to render the image in greyscale
     */
    private Style createGreyscaleStyle(AbstractGridCoverage2DReader reader) {
        GridCoverage2D cov = null;
        try {
            cov = reader.read(null);
        } catch (IOException giveUp) {
            throw new RuntimeException(giveUp);
        }
        int numBands = cov.getNumSampleDimensions();
        Integer[] bandNumbers = new Integer[numBands];
        for (int i = 0; i < numBands; i++) {
            bandNumbers[i] = i + 1;
        }
        Object selection = JOptionPane.showInputDialog(
                frame,
                "Band to use for greyscale display",
                "Select an image band",
                JOptionPane.QUESTION_MESSAGE,
                null,
                bandNumbers,
                1);
        if (selection != null) {
            int band = ((Number) selection).intValue();
            return createGreyscaleStyle(band);
        }
        return null;
    }

    /**
     * Create a Style to display the specified band of the GeoTIFF image as a
     * greyscale layer. <p> This method is a helper for createGreyScale() and is
     * also called directly by the displayLayers() method when the application
     * first starts.
     *
     * @param band the image band to use for the greyscale display
     *
     * @return a new Style instance to render the image in greyscale
     */
    private Style createGreyscaleStyle(int band) {
        ContrastEnhancement ce = sf.contrastEnhancement(ff.literal(1.0), ContrastMethod.NORMALIZE);
        SelectedChannelType sct = sf.createSelectedChannelType(String.valueOf(band), ce);

        RasterSymbolizer sym = sf.getDefaultRasterSymbolizer();
        ChannelSelection sel = sf.channelSelection(sct);
        sym.setChannelSelection(sel);

        return SLD.wrapSymbolizers(sym);
    }

    // docs end create greyscale style
    // docs start create rgb style
    /**
     * This method examines the names of the sample dimensions in the provided
     * coverage looking for "red...", "green..." and "blue..." (case insensitive
     * match). If these names are not found it uses bands 1, 2, and 3 for the
     * red, green and blue channels. It then sets up a raster symbolizer and
     * returns this wrapped in a Style.
     *
     * @return a new Style object containing a raster symbolizer set up for RGB
     * image
     */
    private Style createRGBStyle(AbstractGridCoverage2DReader reader) {
        GridCoverage2D cov = null;
        try {
            cov = reader.read(null);
        } catch (IOException giveUp) {
            throw new RuntimeException(giveUp);
        }
        // We need at least three bands to create an RGB style
        int numBands = cov.getNumSampleDimensions();
        if (numBands < 3) {
            return null;
        }
        // Get the names of the bands
        String[] sampleDimensionNames = new String[numBands];
        for (int i = 0; i < numBands; i++) {
            GridSampleDimension dim = cov.getSampleDimension(i);
            sampleDimensionNames[i] = dim.getDescription().toString();
        }
        final int RED = 0, GREEN = 1, BLUE = 2;
        int[] channelNum = {-1, -1, -1};
        // We examine the band names looking for "red...", "green...", "blue...".
        // Note that the channel numbers we record are indexed from 1, not 0.
        for (int i = 0; i < numBands; i++) {
            String name = sampleDimensionNames[i].toLowerCase();
            if (name != null) {
                if (name.matches("red.*")) {
                    channelNum[RED] = i + 1;
                } else if (name.matches("green.*")) {
                    channelNum[GREEN] = i + 1;
                } else if (name.matches("blue.*")) {
                    channelNum[BLUE] = i + 1;
                }
            }
        }
        // If we didn't find named bands "red...", "green...", "blue..."
        // we fall back to using the first three bands in order
        if (channelNum[RED] < 0 || channelNum[GREEN] < 0 || channelNum[BLUE] < 0) {
            channelNum[RED] = 1;
            channelNum[GREEN] = 2;
            channelNum[BLUE] = 3;
        }
        // Now we create a RasterSymbolizer using the selected channels
        SelectedChannelType[] sct = new SelectedChannelType[cov.getNumSampleDimensions()];
        ContrastEnhancement ce = sf.contrastEnhancement(ff.literal(1.0), ContrastMethod.NORMALIZE);
        for (int i = 0; i < 3; i++) {
            sct[i] = sf.createSelectedChannelType(String.valueOf(channelNum[i]), ce);
        }
        RasterSymbolizer sym = sf.getDefaultRasterSymbolizer();
        ChannelSelection sel = sf.channelSelection(sct[RED], sct[GREEN], sct[BLUE]);
        sym.setChannelSelection(sel);

        return SLD.wrapSymbolizers(sym);
    }

    private void addRasterFile(File rasterFile) {
        AbstractGridFormat format = GridFormatFinder.findFormat(rasterFile);
        AbstractGridCoverage2DReader reader = format.getReader(rasterFile);
        //Style rasterStyle = createGreyscaleStyle(1);
        Style rasterStyle = createRGBStyle(reader);
        GridReaderLayer rasterLayer = new GridReaderLayer(reader, rasterStyle);
        map.addLayer(rasterLayer);
        frame.repaint();
    }

    private void addFeatureFile(File shpFile) throws IOException {
        // Connect to the shapefile
        FileDataStore dataStore = FileDataStoreFinder.getDataStore(shpFile);
        SimpleFeatureSource shapefileSource = dataStore.getFeatureSource();
        // Create a basic style with yellow lines and no fill
        Style shpStyle = SLD.createPolygonStyle(Color.YELLOW, null, 0.0f);
        FeatureLayer shpLayer = new FeatureLayer(shapefileSource, shpStyle);
        map.addLayer(shpLayer);
        frame.repaint();
    }

    // docs start get layers
    /**
     * Prompts the user for a GeoTIFF file and a Shapefile and passes them to
     * the displayLayers method
     */
    private void getLayersFromImageFile() throws Exception {
        List<Parameter<?>> list = new ArrayList<Parameter<?>>();

        list.add(new Parameter<File>("image", File.class, "Image",
                "GeoTiff or World+Image to display as basemap",
                new KVP(Parameter.EXT, "tif", Parameter.EXT, "jpg")));

//        
//        list.add(new Parameter<File>("shape", File.class, "Shapefile",
//                "Shapefile contents to display", new KVP(Parameter.EXT, "shp")));

        JParameterListWizard wizard = new JParameterListWizard("Image Lab", "Fill in the following layers", list);
        int finish = wizard.showModalDialog();

        if (finish != JWizard.FINISH) {
            System.exit(0);
        }
        File imageFile = (File) wizard.getConnectionParameters().get("image");
        //File shapeFile = (File) wizard.getConnectionParameters().get("shape");
        addRasterFile(imageFile);
        //addFeatureFile(shapeFile);
    }

    // docs start get layers
    /**
     * Prompts the user for a GeoTIFF file and a Shapefile and passes them to
     * the displayLayers method
     */
    private void getLayersFromSheapFile() throws Exception {
        List<Parameter<?>> list = new ArrayList<Parameter<?>>();
//        list.add(new Parameter<File>("image", File.class, "Image",
//                "GeoTiff or World+Image to display as basemap",
//                new KVP(Parameter.EXT, "tif", Parameter.EXT, "jpg")));
        list.add(new Parameter<File>("shape", File.class, "Shapefile",
                "Shapefile contents to display", new KVP(Parameter.EXT, "shp")));

        JParameterListWizard wizard = new JParameterListWizard("Image Lab",
                "Fill in the following layers", list);
        int finish = wizard.showModalDialog();

        if (finish != JWizard.FINISH) {
            System.exit(0);
        }
//        File imageFile = (File) wizard.getConnectionParameters().get("image");
        File shapeFile = (File) wizard.getConnectionParameters().get("shape");
        // addRasterFile(imageFile);
        addFeatureFile(shapeFile);
    }

    private void prepareTheMap() throws IOException, ServiceException {
        // Set up a MapContent with the two layers
        map = new MapContent();
        map.setTitle("ImageLab");
//        
//        // display a data store file chooser dialog for shapefiles
//        URL capabilitiesURL = WMSChooser.showChooseWMS();
//        if( capabilitiesURL == null ){
//            System.exit(0); // canceled
//        }
//        WebMapServer wms = new WebMapServer( capabilitiesURL );        
//        
//        List<Layer> wmsLayers = WMSLayerChooser.showSelectLayer( wms );
//        if( wmsLayers == null ){
//            JOptionPane.showMessageDialog(null, "Could not connect - check url");
//            System.exit(0);
//        }
//
//        map.setTitle( wms.getCapabilities().getService().getTitle() );
//        
//        for( Layer wmsLayer : wmsLayers ){
//            WMSLayer displayLayer = new WMSLayer(wms, wmsLayer );
//            map.addLayer(displayLayer);
//        }



    }
}
// docs end source