package edu.unc.ceccr.chembench.workflows.modelingPrediction;

import edu.unc.ceccr.chembench.global.Constants;
import edu.unc.ceccr.chembench.persistence.Descriptors;
import edu.unc.ceccr.chembench.persistence.Predictor;
import edu.unc.ceccr.chembench.persistence.PredictorRepository;
import edu.unc.ceccr.chembench.utilities.*;
import edu.unc.ceccr.chembench.workflows.datasets.DatasetFileOperations;
import edu.unc.ceccr.chembench.workflows.datasets.StandardizeMolecules;
import edu.unc.ceccr.chembench.workflows.descriptors.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class RunSmilesPrediction {
    private static final Logger logger = LoggerFactory.getLogger(RunSmilesPrediction.class);
    private static PredictorRepository predictorRepository;

    public static String[] predictSmilesSdf(String workingDir, String username, Predictor predictor, AllDescriptors descriptorSetListObj) throws Exception {
        Path wd = new File(workingDir).toPath();
        if (!Files.exists(wd)) {
            logger.info("Working directory doesn't exist, creating it: " + wd.toString());
            Files.createDirectory(wd);
        }

        String sdfile = workingDir + "smiles.sdf";
        logger.debug("Running predictSmilesSdf in dir " + workingDir);

        /* copy the predictor to the workingDir. */
        String predictorUsername = predictor.getUserName();
        String fromDir = Constants.CECCR_USER_BASE_PATH + predictorUsername + "/PREDICTORS/" + predictor.getName() +
                "/";

        logger.debug("Copying predictor files from " + fromDir);
        // (true) means get all files
        // (false) means only get train_0.x file
        CopyJobFiles.getPredictorFiles(predictorUsername, predictor, workingDir, false);
        logger.debug("Copying complete. Generating descriptors. ");

        /* generate ISIDA descriptor for smiles.sdf*/
        if (predictor.getDescriptorGeneration().equals(Constants.ISIDA)) {
            generateIsidaDescriptorsForSdf(sdfile);
        }

        /* create the descriptors for the chemical and read them in */
        List<String> descriptorNames = new ArrayList<>();
        List<Descriptors> descriptorValueMatrix = new ArrayList<>();
        List<String> chemicalNames = DatasetFileOperations.getSdfCompoundNames(sdfile);

        descriptorSetListObj.readDescriptorSets(sdfile, descriptorNames, descriptorValueMatrix);

        logger.debug("Normalizing descriptors to fit predictor.");

        String descriptorString = Utility.stringListToString(descriptorNames);
        WriteDescriptors
                .writePredictionXFile(chemicalNames, descriptorValueMatrix, descriptorString, sdfile + ".renorm.x",
                        workingDir + "train_0.x", predictor.getScalingType());

        /* read prediction output */
        List<String> predValueArray = new ArrayList<>();
        if (predictor.getModelMethod().equals(Constants.RANDOMFOREST)) {
            Path predictorDir = predictor.getDirectoryPath(predictorRepository);
            ScikitRandomForestPrediction pred =
                    RandomForest.predict(predictorDir, Paths.get(workingDir), "smiles.sdf" + ".renorm.x");
            Map<String, Double> predictions = pred.getPredictions();
            for (String key : predictions.keySet()) {
                predValueArray.add(predictions.get(key).toString());
            }
        } else if (predictor.getModelMethod().equals(Constants.RANDOMFOREST_R)) {
            // run prediction
            String xFile = "smiles.sdf.renorm.x";
            String newXFile = "RF_" + xFile;
            LegacyRandomForest.preProcessXFile(predictor.getScalingType(), xFile, newXFile, workingDir);

            String scriptDir = Constants.CECCR_BASE_PATH + Constants.SCRIPTS_PATH;
            String predictScript = scriptDir + Constants.RF_PREDICT_RSCRIPT;
            String modelsListFile = "models.list";
            String command = "Rscript --vanilla " + predictScript + " --scriptsDir " + scriptDir + " --workDir " +
                    workingDir + " --modelsListFile " + modelsListFile + " --xFile " + newXFile;

            RunExternalProgram.runCommandAndLogOutput(command, workingDir, "randomForestPredict");

            // get output
            String outputFile = Constants.PRED_OUTPUT_FILE + ".preds";
            logger.debug("Reading consensus prediction file: " + workingDir + outputFile);
            BufferedReader in = new BufferedReader(new FileReader(workingDir + outputFile));
            String inputString;
        /* first line is the header with the model names */
            in.readLine();
            while ((inputString = in.readLine()) != null && !inputString.equals("")) {
            /*
             * Note: [0] is the compound name and the following are the
             * predicted values.
             */
                String[] data = inputString.split("\\s+");

                for (int i = 1; i < data.length; i++) {
                    predValueArray.add(data[i]);
                }
            }
            in.close();
        } else {
            // unsupported modeling type
            String logString =
                    String.format("Model method not supported for SMILES predictions: PREDICTOR=%s, METHOD=%s",
                            predictor.getName(), predictor.getModelMethod());
            logger.warn(logString);
        }

        /* calculate stddev */
        double sum = 0;
        double mean = 0;
        if (predValueArray.size() > 0) {
            for (String predValue : predValueArray) {
                sum += Float.parseFloat(predValue);
            }
            mean = sum / predValueArray.size();
        }

        double stddev = 0;
        if (predValueArray.size() > 1) {
            for (String predValue : predValueArray) {
                double distFromMeanSquared = Math.pow((Double.parseDouble(predValue) - mean), 2);
                stddev += distFromMeanSquared;
            }
            /* divide sum then take sqrt to get stddev */
            stddev = Math.sqrt(stddev / predValueArray.size());
        }

        logger.debug("prediction: " + mean);
        logger.debug("stddev: " + stddev);

        /* format numbers nicely and return them */
        String[] prediction = new String[3];
        prediction[0] = "" + predValueArray.size();
        if (predValueArray.size() > 0) {
            String predictedValue = DecimalFormat.getInstance().format(mean).replaceAll(",", "");
            logger.debug("String-formatted prediction: " + predictedValue);
            predictedValue = (Utility.roundSignificantFigures(predictedValue, Constants.REPORTED_SIGNIFICANT_FIGURES));
            prediction[1] = predictedValue;
        } else {
            prediction[1] = "N/A";
            if (predictor.getModelMethod().equals(Constants.KNNGA) || predictor.getModelMethod()
                    .equals(Constants.KNNSA)) {
                prediction[1] += "- Cutoff Too Low";
            }
        }
        if (predValueArray.size() > 1) {
            String stdDevStr = DecimalFormat.getInstance().format(stddev).replaceAll(",", "");
            logger.debug("String-formatted stddev: " + stdDevStr);
            stdDevStr = (Utility.roundSignificantFigures(stdDevStr, Constants.REPORTED_SIGNIFICANT_FIGURES));
            prediction[2] = stdDevStr;
        } else {
            prediction[2] = "N/A";
        }

        return prediction;
    }

    public static void smilesToSdf(String smiles, String smilesDir) throws Exception {
        /*
         * takes in a SMILES string and produces an SDF file from it. Returns
         * the file path as a string.
         */

        logger.debug("Running smilesToSdf with SMILES: " + smiles);

        /* set up the directory, just in case it's not there yet. */
        File dir = new File(smilesDir);
        dir.mkdirs();

        /* write SMILES string to file */
        FileWriter fstream = new FileWriter(smilesDir + "tmp.smiles");
        BufferedWriter out = new BufferedWriter(fstream);
        out.write(smiles + " 1");
        out.close();

        String sdfFileName = "smiles.sdf";

        /* execute molconvert to change it to SDF */
        String execstr = "molconvert sdf " + smilesDir + "tmp.smiles -o " + smilesDir + sdfFileName;

        RunExternalProgram.runCommandAndLogOutput(execstr, smilesDir, "molconvert");

        // standardize the SDF
        StandardizeMolecules.standardizeSdf(sdfFileName, sdfFileName + ".standardize", smilesDir);
        File standardized = new File(smilesDir + sdfFileName + ".standardize");
        if (standardized.exists()) {
            /* replace old SDF with new standardized SDF */
            FileAndDirOperations.copyFile(smilesDir + sdfFileName + ".standardize", smilesDir + sdfFileName);
            FileAndDirOperations.deleteFile(smilesDir + sdfFileName + ".standardize");
        }

        StandardizeSdfFormat.addNameTag("", "SMILES", smilesDir + sdfFileName, smilesDir + sdfFileName + ".addNameTag");

        logger.debug("Finished smilesToSdf");
    }

    public static void generateIsidaDescriptorsForSdf(String sdfile) throws Exception {
        DescriptorIsida descriptorIsida = new DescriptorIsida();
        descriptorIsida.generateDescriptors(sdfile, sdfile);
    }

    @Autowired
    public void setPredictorRepository(PredictorRepository predictorRepository) {
        RunSmilesPrediction.predictorRepository = predictorRepository;
    }
}
