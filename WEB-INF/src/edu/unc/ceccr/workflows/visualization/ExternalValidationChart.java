package edu.unc.ceccr.workflows.visualization;

import edu.unc.ceccr.global.Constants;
import edu.unc.ceccr.persistence.ExternalValidation;
import edu.unc.ceccr.persistence.HibernateUtil;
import edu.unc.ceccr.persistence.Predictor;
import edu.unc.ceccr.utilities.PopulateDataObjects;
import org.apache.commons.validator.GenericValidator;
import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.entity.StandardEntityCollection;
import org.jfree.chart.labels.CustomXYToolTipGenerator;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.urls.CustomXYURLGenerator;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.HorizontalAlignment;
import org.jfree.ui.RectangleEdge;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class ExternalValidationChart {
    private static Logger logger = Logger.getLogger(ExternalValidationChart.class.getName());

    public static void
    createChart(Predictor predictor, String currentFoldNumber) throws Exception {
        /*
         * Ext validation plot is generated by JFreeChart. The chart will be
         * created using the command: ChartFactory.createXYLineChart(title,
         * xAxisLabel, yAxisLabel, dataset, orientation, legend, tooltips,
         * urls) "dataset" contains the chart data, which is a collection of
         * XYSeries objects. In this chart, the first series is the set of
         * observed/predicted points. The second series is the highlighted
         * points (used when viewing one fold of an n-fold predictor.) The
         * third series holds a min and max, and is used to set the chart
         * size. After that, there is one series *per point* for each of the
         * standard deviation bars that go up and down from each point.
         */

        String project = predictor.getName();
        String user = predictor.getUserName();

        Session session = HibernateUtil.getSession();

        List<ExternalValidation> extValidation;

        // used to highlight one child of an nfold
        List<ExternalValidation> highlightedExtValidation
                = new ArrayList<ExternalValidation>();

        List<Predictor> childPredictors = PopulateDataObjects
                .getChildPredictors(predictor, session);
        if (childPredictors.size() != 0) {
            // get external set for each
            extValidation = new ArrayList<ExternalValidation>();
            for (int i = 0; i < childPredictors.size(); i++) {
                Predictor cp = childPredictors.get(i);
                List<ExternalValidation> childExtVals = PopulateDataObjects
                        .getExternalValidationValues(cp.getId(), session);
                if (currentFoldNumber.equals("" + (i + 1))) {
                    highlightedExtValidation.addAll(childExtVals);
                } else {
                    extValidation.addAll(childExtVals);
                }
            }
        } else {
            extValidation = PopulateDataObjects.getExternalValidationValues(
                    predictor.getId(), session);
        }
        if (extValidation.size() == 0) {
            return;
        }

        logger.debug("extval size: " + extValidation.size());

        int index = 0;
        float high, low;
        session.close();
        ExternalValidation extv = null;

        for (int i = 0; i < extValidation.size(); i++) {
            if (extValidation.get(i).getNumModels() == 0) {
                // no models predicted this point; remove it
                // so it doesn't skew the chart.
                extValidation.remove(i);
                i--;
            }
        }
        for (int i = 0; i < highlightedExtValidation.size(); i++) {
            if (highlightedExtValidation.get(i).getNumModels() == 0) {
                // no models predicted this point; remove it
                // so it doesn't skew the chart.
                highlightedExtValidation.remove(i);
                i--;
            }
        }

        final XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        final Stroke stroke = new BasicStroke(0.7f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER, 10f, new float[]{3.5f}, 0.0f);

        HashMap<Integer, String> map = new HashMap<Integer, String>();

        List<String> tooltipList = new ArrayList<String>();

        XYSeries pointSeries = new XYSeries(0, false);
        XYSeries highlightedPointSeries = new XYSeries(0, false);

        List<XYSeries> stdDevList = new ArrayList<XYSeries>();
        List<XYSeries> highlightedStdDevList = new ArrayList<XYSeries>();

        Iterator<ExternalValidation> it = extValidation.iterator();
        while (it.hasNext()) {
            extv = it.next();
            pointSeries.add(extv.getActualValue(), extv.getPredictedValue());
            map.put(index, extv.getCompoundId());
            if (extv.getNumModels() > 3) {
                tooltipList.add("Compound ID: " + extv.getCompoundId()
                        + "<br/>" + extv.getPredictedValue() + " &#177; "
                        + extv.getStandDev());
                XYSeries series = new XYSeries("");
                if (GenericValidator.isFloat(extv.getStandDev())) {
                    high = extv.getPredictedValue()
                            + Float.parseFloat(extv.getStandDev());
                    low = extv.getPredictedValue()
                            - Float.parseFloat(extv.getStandDev());
                } else {
                    high = extv.getPredictedValue();
                    low = extv.getPredictedValue();
                }
                series.add(extv.getActualValue(), high);
                series.add(extv.getActualValue(), low);
                stdDevList.add(series);
            } else {
                tooltipList.add("Compound ID: " + extv.getCompoundId()
                        + "<br/>" + extv.getPredictedValue());
            }
            index++;
        }

        it = highlightedExtValidation.iterator();
        while (it.hasNext()) {
            extv = it.next();
            highlightedPointSeries.add(extv.getActualValue(), extv
                    .getPredictedValue());
            map.put(index, extv.getCompoundId());
            if (extv.getNumModels() > 3) {
                tooltipList.add("Compound ID: " + extv.getCompoundId()
                        + "<br/>" + extv.getPredictedValue() + " &#177; "
                        + extv.getStandDev());
                XYSeries series = new XYSeries("");
                if (GenericValidator.isFloat(extv.getStandDev())) {
                    high = extv.getPredictedValue()
                            + Float.parseFloat(extv.getStandDev());
                    low = extv.getPredictedValue()
                            - Float.parseFloat(extv.getStandDev());
                } else {
                    high = extv.getPredictedValue();
                    low = extv.getPredictedValue();
                }
                series.add(extv.getActualValue(), high);
                series.add(extv.getActualValue(), low);
                highlightedStdDevList.add(series);
            } else {
                tooltipList.add("Compound ID: " + extv.getCompoundId()
                        + "<br/>" + extv.getPredictedValue());
            }
            index++;
        }

        // adjust axes of chart so that points will be well placed
        double min = Math.min(MinRange(extValidation, 0), MinRange(
                extValidation, 1));
        double max = Math.max(MaxRange(extValidation, 0), MaxRange(
                extValidation, 1));

        XYSeries minAndMax = new XYSeries("");
        minAndMax.add(min, min);
        minAndMax.add(max, max);

        XYSeriesCollection ds = new XYSeriesCollection();
        ds.addSeries(pointSeries);
        ds.addSeries(highlightedPointSeries);
        ds.addSeries(minAndMax);

        // Standard deviation lines
        int i = 3;
        Iterator<XYSeries> it2 = stdDevList.iterator();
        while (it2.hasNext()) {
            ds.addSeries(it2.next());
            renderer.setSeriesLinesVisible(i, true);
            renderer.setSeriesShapesVisible(i, true);
            if (highlightedExtValidation.size() > 0) {
                renderer.setSeriesLinesVisible(i, false);
                renderer.setSeriesShapesVisible(i, false);
                renderer.setSeriesPaint(i, Color.DARK_GRAY);
            } else {
                renderer.setSeriesPaint(i, Color.RED);
            }
            renderer.setSeriesStroke(i, stroke);
            renderer.setSeriesItemLabelsVisible(i, false);
            renderer.setSeriesShape(i, new Rectangle2D.Double(-4.0, -4.0,
                    8.0, 0.10));
            i++;
        }

        i = 3 + stdDevList.size();
        it2 = highlightedStdDevList.iterator();
        while (it2.hasNext()) {
            ds.addSeries(it2.next());
            renderer.setSeriesLinesVisible(i, true);
            renderer.setSeriesShapesVisible(i, true);
            if (highlightedExtValidation.size() > 0) {
                renderer.setSeriesPaint(i, Color.RED);
            } else {
                renderer.setSeriesPaint(i, Color.DARK_GRAY);
            }
            renderer.setSeriesStroke(i, stroke);
            renderer.setSeriesItemLabelsVisible(i, false);
            renderer.setSeriesShape(i, new Rectangle2D.Double(-4.0, -4.0,
                    8.0, 0.10));
            i++;
        }
        // end add standard deviation lines

        CustomXYToolTipGenerator ctg = new CustomXYToolTipGenerator();
        ctg.addToolTipSeries(tooltipList);

        CustomXYURLGenerator cxyg = new CustomXYURLGenerator();
        cxyg.addURLSeries(customizedURLs(ds, map, project, user));

        // for the base point set
        renderer.setSeriesItemLabelsVisible(0, true);
        renderer.setSeriesToolTipGenerator(0, ctg);
        renderer.setSeriesLinesVisible(0, false);
        if (highlightedExtValidation.size() > 0) {
            renderer.setSeriesPaint(0, Color.DARK_GRAY);
            renderer.setSeriesShape(0, new Ellipse2D.Double(-1.5, -1.5, 3.0,
                    3.0));
        } else {
            renderer.setSeriesPaint(0, Color.RED);
            renderer.setSeriesShape(0, new Ellipse2D.Double(-3.0, -3.0, 6.0,
                    6.0));
        }
        renderer.setSeriesShapesVisible(0, true);
        renderer.setURLGenerator(cxyg);
        renderer.setSeriesToolTipGenerator(0, ctg);

        // for the highlighted set
        renderer.setSeriesItemLabelsVisible(1, true);
        renderer.setSeriesShape(1, new Ellipse2D.Double(-3.0, -3.0, 6.0, 6.0));
        renderer.setSeriesToolTipGenerator(1, ctg);
        renderer.setSeriesPaint(1, Color.RED);
        renderer.setSeriesLinesVisible(1, false);
        renderer.setSeriesShapesVisible(1, true);
        renderer.setURLGenerator(cxyg);
        renderer.setSeriesToolTipGenerator(0, ctg);

        final ChartRenderingInfo info = new ChartRenderingInfo(
                new StandardEntityCollection());

        JFreeChart chart = ChartFactory.createXYLineChart(
                "External Validation Set", "Observed", "Predicted", ds,
                PlotOrientation.VERTICAL, false, true, true);

        chart.setBackgroundPaint(new Color(0xDA, 0xEC, 0xF8));
        TextTitle tt = new TextTitle(" http://chembench.mml.unc.edu",
                new Font("Dialog", Font.PLAIN, 11));
        tt.setPosition(RectangleEdge.BOTTOM);
        tt.setHorizontalAlignment(HorizontalAlignment.RIGHT);
        tt.setMargin(0.0, 0.0, 4.0, 4.0);
        chart.addSubtitle(tt);

        final XYPlot plot = chart.getXYPlot();

        plot.setBackgroundPaint(Color.white);
        plot.setForegroundAlpha(0.5f);
        plot.setDomainGridlinePaint(Color.lightGray);
        plot.setRangeGridlinePaint(Color.lightGray);
        plot.setRenderer(renderer);

        final NumberAxis Yaxis = (NumberAxis) plot.getRangeAxis();
        Yaxis.setAutoRange(false);
        Yaxis.setAutoRangeMinimumSize(0.01);
        Yaxis.setRange(min, max);

        final NumberAxis Xaxis = (NumberAxis) plot.getDomainAxis();
        Xaxis.setAutoRange(false);
        Xaxis.setAutoRangeMinimumSize(0.01);
        Xaxis.setRange(min, max);

        String basePath = Constants.CECCR_USER_BASE_PATH + user
                + "/PREDICTORS/" + project + "/";
        if (!currentFoldNumber.equals("0")) {
            int numChildren = predictor.getChildIds().split("\\s+").length;
            String childPredName = project + "_fold_" + currentFoldNumber
                    + "_of_" + numChildren;
            basePath += childPredName + "/";
        }
        logger.debug("Writing external validation chart to file: "
                + basePath + "mychart.jpeg");
        FileOutputStream fos_jpg = new FileOutputStream(basePath
                + "mychart.jpeg");
        ChartUtilities.writeChartAsJPEG(fos_jpg, 1.0f, chart, 650, 650, info);
        fos_jpg.close();

        FileOutputStream fos_cri = new FileOutputStream(basePath
                + "mychart.map");
        PrintWriter pw = new PrintWriter(fos_cri);
        fos_cri.close();
        pw.flush();

        final InputStream input = new BufferedInputStream(
                new FileInputStream(basePath + "mychart.map"));
        input.close();

    }

    protected static List<String>
    customizedURLs(XYDataset ds,
                   HashMap<Integer, String> map,
                   String predictorName,
                   String user) {
        List<String> list = new ArrayList<String>();
        String url;

        for (int i = 0; i < map.size(); i++) {
            url = "javascript: void(window.open('sketch?project="
                    + predictorName + "&projectType=modelbuilder&user="
                    + user + "&compoundId=" + map.get(i) + "', 'window"
                    + new java.util.Date().getTime()
                    + "','width=380, height=400'));  ";
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                logger.error(e);
            }
            list.add(url);
        }

        return list;
    }

    protected static double MinRange(List<ExternalValidation> extValidation,
                                     int option) {
        double min = 100.00;
        double extvalue;
        ExternalValidation extv = null;
        Iterator<ExternalValidation> it = extValidation.iterator();

        while (it.hasNext()) {
            extv = it.next();

            if (option == 0) {
                extvalue = extv.getPredictedValue();
            } else {
                extvalue = extv.getActualValue();
            }

            if (min > extvalue) {
                min = extvalue;
            }
        }

        return min - 0.5;
    }

    protected static double MaxRange(List<ExternalValidation> extValidation,
                                     int option) {
        double max = -100.00;
        double extvalue;
        ExternalValidation extv = null;
        Iterator<ExternalValidation> it = extValidation.iterator();

        while (it.hasNext()) {
            extv = it.next();

            if (option == 0) {
                extvalue = extv.getPredictedValue();
            } else {
                extvalue = extv.getActualValue();
            }

            if (max < extvalue) {
                max = extvalue;
            }
        }

        return max + 0.5;

    }

}