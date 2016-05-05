package edu.unc.ceccr.chembench.workflows.modelingPrediction;

import com.google.common.collect.Lists;
import edu.unc.ceccr.chembench.global.Constants;
import edu.unc.ceccr.chembench.persistence.*;
import edu.unc.ceccr.chembench.utilities.FileAndDirOperations;
import edu.unc.ceccr.chembench.utilities.RunExternalProgram;
import edu.unc.ceccr.chembench.workflows.datasets.DatasetFileOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class KnnPlus {
    private static final Logger logger = LoggerFactory.getLogger(KnnPlus.class);

    private static String getKnnPlusCommandFromParams(KnnPlusParameters knnPlusParameters, String actFileDataType,
                                                      String modelType) {
        // this converts the parameters entered on the web page into
        // command-line
        // arguments formatted to work with knn+.
        // The comments in this function are excerpts from the knn+ help file.

        String command = "./knn+ rand_sets.list";

        // '-OUT=...' - output file
        command += " -OUT=" + Constants.KNNPLUS_MODELS_FILENAME;

        if (actFileDataType.equals(Constants.CONTINUOUS)) {
            // '-M=...' - model type: 'CNT' continuous <def.>,'CTG' -
            // category,'CLS' - classes
            command += " -M=CNT";
        } else if (actFileDataType.equals(Constants.CATEGORY)) {
            // '-M=...' - model type: 'CNT' continuous <def.>,'CTG' -
            // category,'CLS' - classes
            command += " -M=CTG";
        }

        if (modelType.equals(Constants.KNNGA)) {
            // Number of dimensions, min-max. There is no step for genetic
            // algorithm.
            // Example: '-D=5@50'
            command += " -D=" + knnPlusParameters.getKnnMinNumDescriptors().trim() + "@" + knnPlusParameters
                    .getKnnMaxNumDescriptors().trim();

            // '-GA@...' - Genetic Algorithm settings: e.g.
            // -GA@N=500@D=1000@S=20@V=-4@G=7
            command += " -O=GA GA";

            // '..@N=' - population size;
            command += "@N=" + knnPlusParameters.getGaPopulationSize().trim();

            // '..@D=' - max.#generations;
            command += "@N=" + knnPlusParameters.getGaMaxNumGenerations().trim();

            // '..@S=' - #stable generations to stop
            command += "@S=" + knnPlusParameters.getGaNumStableGenerations().trim();

            // '..@V=' - minimum fitness difference to proceed
            command += "@V=" + knnPlusParameters.getGaMinFitnessDifference().trim();

            // '..@G=' - group size for tournament ('TOUR') selection of
            // parents
            command += "@G=" + knnPlusParameters.getGaTournamentGroupSize().trim();
        } else if (modelType.equals(Constants.KNNSA)) {
            // Number of dimensions, min-max-step. Step can't be used in
            // genetic alg.
            // Example: '-D=5@50@3'
            command += " -D=" + knnPlusParameters.getKnnMinNumDescriptors().trim() + "@" + knnPlusParameters
                    .getKnnMaxNumDescriptors().trim() + "@" + knnPlusParameters.getKnnDescriptorStepSize().trim();

            // '-SA@...' - Simulated Annealing settings: e.g.
            // -SA@B=3@TE=-2@K=0.6@DT=-3@ET=-5
            command += " -O=SA -SA";

            // '..@N=' - #SA runs to repeat; '..@D=' - #mutations at each T
            command += "@N=" + knnPlusParameters.getSaNumRuns().trim();

            // '..@T0=x' - start T (10^x);
            command += "@T0=" + knnPlusParameters.getSaLogInitialTemp().trim();

            // '..@TE=' - final T;
            command += "@TE=" + knnPlusParameters.getSaFinalTemp().trim();

            // '..@DT=' - convergence range of T
            command += "@DT=" + knnPlusParameters.getSaTempConvergence().trim();

            // '..@M=' - mutation probability per dimension
            command += "@M=" + knnPlusParameters.getSaMutationProbabilityPerDescriptor().trim();

            // '..@B=' - #best models to store
            // Constraint: Number of best models must be less than or equal to
            // the number
            // of runs. If #best > #runs, it will set #runs = #best.
            command += "@B=" + knnPlusParameters.getSaNumBestModels().trim();

            // '..@K=' - T decreasing coeff.;
            command += "@K=" + knnPlusParameters.getSaTempDecreaseCoefficient().trim();

        }

        // '-KR=1@9' (to try from 1 to 9 neighbors)
        // Constraint: Max is strictly greater (>) than min. If this is not
        // true,
        // it will automatically user '9' as the maximum, making the modeling
        // take
        // a REALLY FUCKIN' LONG TIME.
        command += " -KR=" + knnPlusParameters.getKnnMinNearestNeighbors().trim() + "@" + knnPlusParameters
                .getKnnMaxNearestNeighbors().trim();

        // '-AD=' - applicability domain: e.g. -AD=0.5, -AD=0.5d1_mxk
        // '0.5' is z-cutoff <def.>; d1 - direct-distance based AD <def. is
        // dist^2>
        // Additional options of AD-checking before making prediction:
        // '_avd' - av.dist to k neighbors should be within AD (traditional)
        // '_mxk' - all k neighbors should be within AD
        // '_avk' - k/2 neighbors within AD, '_mnk' - at least 1 within AD
        // <def.>
        command += " -AD=" + knnPlusParameters.getKnnApplicabilityDomain().trim();

        // '-EVL=...' - model's quality controls; e.g. -EVL=A0.5@0.6
        // For continuous kNN it means q2 >0.5 and R2>0.6
        // A - alternative control-indices; E - error-based
        // V - aver.error based (only for discrete-act.); S - simple
        // post-evaluation
        if ((modelType.equals(Constants.KNNSA) && knnPlusParameters.getKnnSaErrorBasedFit().equalsIgnoreCase("true"))
                || (modelType.equals(Constants.KNNGA) && knnPlusParameters.getKnnGaErrorBasedFit()
                .equalsIgnoreCase("true"))) {
            command +=
                    " -EVL=E" + knnPlusParameters.getKnnMinTraining().trim() + "@" + knnPlusParameters.getKnnMinTest()
                            .trim();
        } else {
            command += " -EVL=" + knnPlusParameters.getKnnMinTraining().trim() + "@" + knnPlusParameters.getKnnMinTest()
                    .trim();
        }
        return command;
    }

    public static String buildKnnPlusModelsLsf(KnnPlusParameters knnPlusParameters, String actFileDataType,
                                               String modelType, String userName, String jobName, String workingDir)
            throws Exception {
        // starts modeling process
        // returns the LSF Job ID

        // remove any existing output files from workingDir/ and
        // workingDir/yRandom/
        try {
            if ((new File(workingDir + "models_knn+.log")).exists()) {
                FileAndDirOperations.deleteFile(workingDir + "models_knn+.log");
            }
            if ((new File(workingDir + "models.tbl")).exists()) {
                FileAndDirOperations.deleteFile(workingDir + "models.tbl");
            }
            if ((new File(workingDir + "yRandom/" + "models_knn+.log")).exists()) {
                FileAndDirOperations.deleteFile(workingDir + "yRandom/");
            }
            if ((new File(workingDir + "yRandom/" + "models.tbl")).exists()) {
                FileAndDirOperations.deleteFile(workingDir + "yRandom/" + "models.tbl");
            }
        } catch (Exception ex) {
            logger.error("", ex);
        }

        String knnPlusCommand = getKnnPlusCommandFromParams(knnPlusParameters, actFileDataType, modelType);

        // knn+ will automatically convert all input filenames to lowercase.
        // so, our list file has to be lowercase.
        FileAndDirOperations.copyFile(workingDir + "RAND_sets.list", workingDir + "rand_sets.list");

        if (new File(workingDir + "yRandom/RAND_sets.list").exists()) {
            FileAndDirOperations.copyFile(workingDir + "yRandom/RAND_sets.list", workingDir + "yRandom/rand_sets.list");
        }

        FileOutputStream fout;
        PrintStream out;
        fout = new FileOutputStream(workingDir + "bsubKnnPlus.sh");
        out = new PrintStream(fout);

        out.println("cd " + workingDir);
        out.println(knnPlusCommand);
        out.println("cd yRandom/");
        out.println(knnPlusCommand);
        out.close();
        fout.close();

        // give exec permissions to script file
        File f = new File(workingDir + "bsubKnnPlus.sh");
        f.setExecutable(true);

        // exec shell script
        String command = "bsub -q patrons ";
        command += "-J cbench_" + userName + "_" + jobName + " -o bsubOutput.txt " + workingDir + "bsubKnnPlus.sh";
        RunExternalProgram.runCommandAndLogOutput(command, workingDir, "bsubKnnPlus");

        String logFilePath = workingDir + "Logs/bsubKnnPlus.log";
        return LsfUtilities.getLsfJobId(logFilePath);

    }

    public static void buildKnnPlusModels(KnnPlusParameters knnPlusParameters, String actFileDataType, String modelType,
                                          String workingDir) throws Exception {

        // knn+ will automatically convert all input filenames to lowercase.
        // so, our list file has to be lowercase.
        FileAndDirOperations.copyFile(workingDir + "RAND_sets.list", workingDir + "rand_sets.list");

        String command = getKnnPlusCommandFromParams(knnPlusParameters, actFileDataType, modelType);

        RunExternalProgram.runCommandAndLogOutput(command, workingDir, "knnPlus");

        // check models for errors, and correct them if found
        checkModelsFile(workingDir);
    }

    public static void predictExternalSet(String userName, String jobName, String workingDir, String cutoffValue)
            throws Exception {
        // Run prediction

        String xfile = "ext_0.x";
        // knn+ models -4PRED=ext_0.x -AD=0.5_avd -OUT=cons_pred;
        // Note the we can use just knn+ instead of ./knn+ here since on the
        // local server knn+ is added to the path.
        String execstr =
                "knn+ models.tbl -4PRED=" + xfile + " -AD=" + cutoffValue + "_avd -OUT=" + Constants.PRED_OUTPUT_FILE;

        RunExternalProgram.runCommandAndLogOutput(execstr, workingDir, "knnPlusPrediction");
    }

    public static List<ExternalValidation> readExternalPredictionOutput(String workingDir, Predictor predictor)
            throws Exception {

        // read prediction output
        String outputFile = "cons_pred_vs_ext_0.preds"; // the ".preds" is
        // added automatically
        // by knn+
        logger.debug("Reading file: " + workingDir + outputFile);
        BufferedReader in = new BufferedReader(new FileReader(workingDir + outputFile));
        String inputString;

        in.readLine(); // header data (junk)
        inputString = in.readLine(); // compound names are here; we'll need
        // them
        String[] compoundNames = inputString.split("\\s+");

        in.readLine(); // junk
        in.readLine(); // junk

        List<List<String>> predictionMatrix = Lists.newArrayList(); // read
        // output
        // file
        // into
        // this
        List<ExternalValidation> predictionValues = Lists.newArrayList(); // to
        // be
        // returned

        // each line of output represents a model
        // (which is really the transpose of the matrix we're looking for...
        // *sigh*)
        while ((inputString = in.readLine()) != null && !inputString.equals("")) {

            List<String> modelValues = Lists.newArrayList();

            // get output for each compound in model
            String[] predValues = inputString.split("\\s+"); // Note: [0] and
            // [1] in this
            // array will be
            // junk.

            // predValues(0) will be model_id, which is just an index.
            // predValues(1) will be AD_distance, which we may want to capture
            // someday.
            // String adDistanceValue = predValues[1];
            for (int i = 2; i < predValues.length; i++) {
                String predictValue = predValues[i];
                modelValues.add(predictValue);
            }
            predictionMatrix.add(modelValues);
        }

        logger.debug("calculating nummodels, avg," + "and stddev for each compound");
        // get the actual (observed) values for each compound
        Map<String, Double> observedValues = DatasetFileOperations.getActFileIdsAndValues(workingDir + "ext_0.a");

        // for each compound, calculate nummodels, avg, and stddev
        int numCompounds = 0;
        if (predictionMatrix.size() > 0) {
            numCompounds = predictionMatrix.get(0).size();
        }
        for (int i = 0; i < numCompounds; i++) {

            try {
                // calculate stddev and avg for each compound
                Float sum = 0f;
                Float mean = 0f;
                int numPredictingModels = predictionMatrix.size();
                logger.debug("doing sum for compound " + i);
                for (int j = 0; j < predictionMatrix.size(); j++) {
                    String predValue = predictionMatrix.get(j).get(i);
                    if (predValue.equalsIgnoreCase("NA")) {
                        numPredictingModels--;
                    } else {
                        sum += Float.parseFloat(predValue);
                    }
                }
                mean = sum / numPredictingModels;

                logger.debug("doing stddev for compound " + i);

                double stddev = 0d;
                for (int j = 0; j < predictionMatrix.size(); j++) {
                    String predValue = predictionMatrix.get(j).get(i);
                    if (!predValue.equalsIgnoreCase("NA")) {
                        float distFromMeanSquared = (float) Math.pow((Double.parseDouble(predValue) - mean), 2);
                        stddev += distFromMeanSquared;
                    }
                }
                // divide sum then take sqrt to get stddev
                stddev = Math.sqrt(stddev / numPredictingModels);

                logger.debug("making predvalue object for compound " + i);
                // create prediction value object
                ExternalValidation ev = new ExternalValidation();
                ev.setNumModels(numPredictingModels);
                ev.setPredictedValue(mean);
                ev.setStandDev("" + stddev);
                ev.setCompoundId(compoundNames[i + 2]);
                ev.setPredictorId(predictor.getId());
                ev.setActualValue((float) (double) observedValues.get(compoundNames[i + 2]));

                predictionValues.add(ev);

            } catch (Exception ex) {
                logger.error("", ex);
            }
        }

        return predictionValues;
    }

    public static int getSaModelingProgress(String workingDir) {
        int count = 0;
        try {
            // cat models_knn+.log | grep q2= | wc > knnSaProgress
            if (new File(workingDir + "models_knn+.log").exists()) {
                BufferedReader br = new BufferedReader(new FileReader(workingDir + "models_knn+.log"));
                String line = "";
                while ((line = br.readLine()) != null) {
                    if (line.contains("q2=")) {
                        count++;
                    } else if (line.contains("CCR=")) {
                        count++;
                    }
                }
                br.close();
            } else {
                return -1;
            }
        } catch (Exception ex) {
            logger.error("", ex);
            return -1;
        }

        return count;
    }

    public static int getGaModelingProgress(String workingDir) {
        int count = 0;
        try {
            if (new File(workingDir + "models_knn+.log").exists()) {
                // cat models_knn+.log | grep Current\ split | wc >
                // knnGaProgress
                BufferedReader br = new BufferedReader(new FileReader(workingDir + "models_knn+.log"));
                String line = "";
                while ((line = br.readLine()) != null) {
                    if (line.contains("Current split")) {
                        count++;
                    }
                }
                br.close();
            } else {
                return -1;
            }
        } catch (Exception ex) {
            logger.error("", ex);
            return -1;
        }

        return count;
    }

    public static List<KnnPlusModel> readModelsFile(String workingDir, Predictor predictor, String isYRandomModel) {
        List<KnnPlusModel> knnPlusModels = Lists.newArrayList();
        try {
            if (!new File(workingDir + "models.tbl").exists()) {
                // in case of no models generated, usually happens in yRandom
                logger.info("no models generated in " + workingDir);
                return knnPlusModels;
            }
            String modelsFile = FileAndDirOperations.readFileIntoString(workingDir + "models.tbl");
            String[] lines = modelsFile.split("\n");
            for (int i = 5; i < lines.length; i++) {
                // each line contains one model
                KnnPlusModel model = new KnnPlusModel();

                // split it to get each piece of information
                String[] tokens = lines[i].split("\t");

                model.setNDims(tokens[2]);
                model.setDimsIDs(tokens[3]);
                model.setDimsNames(tokens[4]);
                model.setKOrR(tokens[5]);

                if (predictor.getActivityType().equalsIgnoreCase(Constants.CONTINUOUS)) {

                    model.setQualityLimitTraining(tokens[6]);
                    model.setNDatapointsTraining(tokens[7]);
                    model.setStdevActTraining(tokens[8]);
                    model.setStdevActCalcTraining(tokens[9]);
                    model.setB01Training(tokens[10]);
                    model.setB11Training(tokens[11]);
                    model.setB02Training(tokens[12]);
                    model.setB12Training(tokens[13]);
                    model.setRTraining(tokens[14]);
                    model.setR2Training(tokens[15]);
                    model.setMSE1Training(tokens[16]);
                    model.setMSE2Training(tokens[17]);
                    model.setF1Training(tokens[18]);
                    model.setF2Training(tokens[19]);
                    model.setK1Training(tokens[20]);
                    model.setK2Training(tokens[21]);
                    model.setR02Training(tokens[22]);
                    model.setR012Training(tokens[23]);
                    model.setMSE01Training(tokens[24]);
                    model.setMSE02Training(tokens[25]);
                    model.setF01Training(tokens[26]);
                    model.setF02Training(tokens[27]);
                    model.setQ2Training(tokens[28]);
                    model.setQPrime2Training(tokens[29]);
                    model.setMAEqTraining(tokens[30]);
                    model.setMAEqPrimeTraining(tokens[31]);
                    model.setMSETraining(tokens[32]);
                    model.setMAETraining(tokens[33]);

                    model.setQualityLimitTest(tokens[34]);
                    model.setNDatapointsTest(tokens[35]);
                    model.setStdevActTest(tokens[36]);
                    model.setStdevActCalcTest(tokens[37]);
                    model.setB01Test(tokens[38]);
                    model.setB11Test(tokens[39]);
                    model.setB02Test(tokens[40]);
                    model.setB12Test(tokens[41]);
                    model.setRTest(tokens[42]);
                    model.setR2Test(tokens[43]);
                    model.setMSE1Test(tokens[44]);
                    model.setMSE2Test(tokens[45]);
                    model.setF1Test(tokens[46]);
                    model.setF2Test(tokens[47]);
                    model.setK1Test(tokens[48]);
                    model.setK2Test(tokens[49]);
                    model.setR02Test(tokens[50]);
                    model.setR012Test(tokens[51]);
                    model.setMSE01Test(tokens[52]);
                    model.setMSE02Test(tokens[53]);
                    model.setF01Test(tokens[54]);
                    model.setF02Test(tokens[55]);
                    model.setQ2Test(tokens[56]);
                    model.setQPrime2Test(tokens[57]);
                    model.setMAEqTest(tokens[58]);
                    model.setMAEqPrimeTest(tokens[59]);
                    model.setMSETest(tokens[60]);
                    model.setMAETest(tokens[61]);
                } else { // category

                    model.setQualityLimitTraining(tokens[6]);
                    model.setNDatapointsTraining(tokens[7]);
                    model.setAccuracyTraining(tokens[8]);
                    model.setCCRNormalizedAccuracyTraining(tokens[9]);
                    model.setAccuracyWithGroupWeightsTraining(tokens[10]);
                    model.setCCRWithGroupWeightsTraining(tokens[11]);
                    model.setAccuracyMaxErrBasedTraining(tokens[12]);
                    model.setCCRMaxErrBasedTraining(tokens[13]);
                    model.setAccuracyAvErrBasedTraining(tokens[14]);
                    model.setCCRAvErrBasedTraining(tokens[15]);

                    model.setQualityLimitTraining(tokens[20]);
                    model.setNDatapointsTraining(tokens[21]);
                    model.setAccuracyTest(tokens[22]);
                    model.setCCRNormalizedAccuracyTest(tokens[23]);
                    model.setAccuracyWithGroupWeightsTest(tokens[24]);
                    model.setCCRWithGroupWeightsTest(tokens[25]);
                    model.setAccuracyMaxErrBasedTest(tokens[26]);
                    model.setCCRMaxErrBasedTest(tokens[27]);
                    model.setAccuracyAvErrBasedTest(tokens[28]);
                    model.setCCRAvErrBasedTest(tokens[29]);
                }

                model.setIsYRandomModel(isYRandomModel);
                knnPlusModels.add(model);
            }

        } catch (Exception ex) {
            logger.error("", ex);
        }
        return knnPlusModels;
    }

    public static void runKnnPlusPrediction(String workingDir, String sdfile) throws Exception {

        // write a dummy .a file because knn+ needs it or it fails
        // bizarrely... X_X
        String actfile = workingDir + sdfile + ".renorm.a";
        BufferedWriter aout = new BufferedWriter(new FileWriter(actfile));
        List<String> compoundNames = DatasetFileOperations.getSdfCompoundNames(workingDir + sdfile);
        for (String compoundName : compoundNames) {
            aout.write(compoundName + " 0\n");
        }
        aout.close();

        String xfile = sdfile + ".renorm.x";
        String execstr =
                "knn+ models.tbl -4PRED=" + xfile + " -AD=" + 99999 + "_avd -OUT=" + Constants.PRED_OUTPUT_FILE;
        RunExternalProgram.runCommandAndLogOutput(execstr, workingDir, "knnPlusPrediction");
    }

    public static List<PredictionValue> readPredictionOutput(String workingDir, Long predictorId,
                                                             String predictionXFile) throws Exception {

        // read prediction output
        String predsFile = predictionXFile.substring(0, predictionXFile.lastIndexOf(".x"))
                + ".preds"; // the ".preds" is added automatically by knn+
        String outputFile =
                Constants.PRED_OUTPUT_FILE + "_vs_" + predsFile.toLowerCase(); // knn+ makes everything lower case
        // for fun
        logger.debug("Reading file: " + workingDir + outputFile);
        BufferedReader in = new BufferedReader(new FileReader(workingDir + outputFile));
        String inputString;

        // The first four lines are all header data
        in.readLine(); // junk
        in.readLine(); // compound names are here, but we get those from the
        // SDF or X instead (knn+ output is buggy on this line)

        logger.debug("reading compound names from X file: " + workingDir + predictionXFile);
        List<String> compoundNames = DatasetFileOperations.getXCompoundNames(workingDir + predictionXFile);

        in.readLine(); // junk
        in.readLine(); // junk

        List<List<String>> predictionMatrix = Lists.newArrayList(); // read
        // output
        // file
        // into
        // this
        List<PredictionValue> predictionValues = Lists.newArrayList(); // holds
        // objects
        // to
        // be
        // returned

        // each line of output represents a model
        // (which is really the transpose of the matrix we're looking for...
        // *sigh*)
        while ((inputString = in.readLine()) != null && !inputString.equals("")) {

            List<String> modelValues = Lists.newArrayList();

            // get output for each compound in model
            String[] predValues = inputString.split("\\s+"); // Note: [0] and
            // [1] in this
            // array will be
            // junk.

            // predValues(0) will be model_id, which is just an index.
            // predValues(1) will be AD_distance, which we may want to capture
            // someday.
            // String adDistanceValue = predValues[1];
            for (int i = 2; i < predValues.length; i++) {
                String predictValue = predValues[i];
                modelValues.add(predictValue);
            }
            predictionMatrix.add(modelValues);
        }

        logger.debug("calculating nummodels, avg, " + "and stddev for each compound");
        // for each compound, calculate nummodels, avg, and stddev
        if (predictionMatrix.size() == 0) {
            // there were no models in the predictor!
            in.close();
            return null;
        }

        int numCompounds = predictionMatrix.get(0).size();
        for (int i = 0; i < numCompounds; i++) {
            try {
                // calculate stddev and avg for each compound
                Float sum = new Float(0);
                Float mean = new Float(0);
                int numPredictingModels = predictionMatrix.size();
                logger.debug("doing sum for compound " + i);

                for (int j = 0; j < predictionMatrix.size(); j++) {
                    String predValue = predictionMatrix.get(j).get(i);
                    if (predValue.equalsIgnoreCase("NA")) {
                        numPredictingModels--;
                    } else {
                        sum += Float.parseFloat(predValue);
                    }
                }
                if (numPredictingModels > 0) {
                    mean = sum / numPredictingModels;
                } else {
                    mean = null;
                }

                logger.debug("doing stddev for compound " + i);

                Float stddev = new Float(0);
                if (numPredictingModels > 0) {
                    for (int j = 0; j < predictionMatrix.size(); j++) {
                        String predValue = predictionMatrix.get(j).get(i);
                        if (!predValue.equalsIgnoreCase("NA")) {
                            float distFromMeanSquared = (float) Math.pow((Double.parseDouble(predValue) - mean), 2);
                            stddev += distFromMeanSquared;
                        }
                    }
                    // divide sum then take sqrt to get stddev
                    stddev = (float) Math.sqrt(stddev / numPredictingModels);
                } else {
                    stddev = null;
                }

                logger.debug("making predvalue object for compound " + i);
                // create prediction value object
                PredictionValue p = new PredictionValue();
                p.setNumModelsUsed(numPredictingModels);
                p.setNumTotalModels(predictionMatrix.size());
                p.setPredictedValue(mean);
                p.setStandardDeviation(stddev);
                p.setCompoundName(compoundNames.get(i));
                p.setPredictorId(predictorId);

                predictionValues.add(p);

            } catch (Exception ex) {
                logger.error("", ex);
            }
        }

        in.close();
        return predictionValues;
    }

    public static void checkModelsFile(String workingDir) throws IOException {
        File modelsFile = new File(workingDir, "models.tbl");
        String modelsFilePath = modelsFile.toString();
        logger.info("Starting models file check of " + modelsFilePath);

        if (!modelsFile.exists()) {
            logger.warn(String.format("Models file %s does not exist!", modelsFilePath));
            return;
        }

        List<String> lines = Files.readAllLines(modelsFile.toPath(), Charset.defaultCharset());
        int originalLineCount = lines.size();

        // skip the first 5 lines as they contain header information
        if (lines.size() > 5) {
            int numFields = 0;
            int lineCount = 5;
            ListIterator<String> iter = lines.listIterator(5);
            while (iter.hasNext()) {
                String currLine = iter.next();
                String[] fields = currLine.split("\t");
                if (numFields == 0) {
                    // save number of fields
                    numFields = fields.length;
                }

                // expect number of fields to remain consistent
                if (fields.length != numFields) {
                    logger.warn(String.format("Discarded line %d of models file %s: " + "expected %d fields, got %d",
                            lineCount, modelsFile, numFields, fields.length));
                    iter.remove();
                }
                lineCount++;
            }
        }

        // only write out new version if something was discarded
        if (originalLineCount > lines.size()) {
            logger.debug("Renaming original models.tbl to " +
                    "models.tbl.old in " + workingDir);
            File modelsFileOld = new File(workingDir, "models.tbl.old");
            if (modelsFileOld.exists()) {
                modelsFileOld.delete();
            }
            modelsFile.renameTo(modelsFileOld);

            FileWriter fw = new FileWriter(modelsFilePath);
            BufferedWriter bw = new BufferedWriter(fw);
            for (String line : lines) {
                bw.write(line);
                bw.newLine();
            }
            bw.close();
            fw.close();
            logger.info("Wrote corrected models file to " + modelsFilePath);
        } else {
            logger.info("Check finished, no changes made.");
        }
    }
}

