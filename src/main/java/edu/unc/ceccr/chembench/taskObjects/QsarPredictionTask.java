package edu.unc.ceccr.chembench.taskObjects;

import com.google.common.collect.Lists;
import edu.unc.ceccr.chembench.global.Constants;
import edu.unc.ceccr.chembench.persistence.*;
import edu.unc.ceccr.chembench.utilities.CopyJobFiles;
import edu.unc.ceccr.chembench.utilities.CreateJobDirectories;
import edu.unc.ceccr.chembench.utilities.FileAndDirOperations;
import edu.unc.ceccr.chembench.utilities.RunExternalProgram;
import edu.unc.ceccr.chembench.workflows.descriptors.DescriptorIsida;
import edu.unc.ceccr.chembench.workflows.descriptors.DescriptorUtility;
import edu.unc.ceccr.chembench.workflows.download.WriteCsv;
import edu.unc.ceccr.chembench.workflows.modelingPrediction.*;
import org.apache.commons.math.stat.descriptive.SummaryStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Configurable(autowire = Autowire.BY_TYPE)
public class QsarPredictionTask extends WorkflowTask {

    private static final Logger logger = LoggerFactory.getLogger(QsarPredictionTask.class);
    // for internal use only
    List<Predictor> selectedPredictors = null;
    private boolean wasRecovered = false;
    private String filePath;
    private String jobName;
    private String sdf;
    private String cutoff;
    private String userName;
    private String selectedPredictorIds;
    private Dataset predictionDataset;
    private String step = Constants.SETUP; // stores what step we're on
    private int allPredsTotalModels = -1; // used by getProgress function
    private List<String> selectedPredictorNames = new ArrayList<>(); // used by getProgress function
    private Prediction prediction;

    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private PredictorRepository predictorRepository;
    @Autowired
    private PredictionRepository predictionRepository;
    @Autowired
    private PredictionValueRepository predictionValueRepository;

    public QsarPredictionTask(String userName, String jobName, String sdf, String cutoff, String selectedPredictorIds,
                              Dataset predictionDataset) throws Exception {
        this.predictionDataset = predictionDataset;
        this.jobName = jobName;
        this.userName = userName;
        this.sdf = sdf;
        this.cutoff = cutoff;
        this.selectedPredictorIds = selectedPredictorIds;
        this.filePath = Constants.CECCR_USER_BASE_PATH + userName + "/" + jobName + "/";
        prediction = null;
    }

    public QsarPredictionTask(Prediction prediction) throws Exception {
        // used when job is recovered on server restart
        wasRecovered = true;
        this.prediction = prediction;
        this.jobName = prediction.getName();
        this.userName = prediction.getUserName();
        this.cutoff = "" + prediction.getSimilarityCutoff().toString();
        this.selectedPredictorIds = prediction.getPredictorIds();
        this.filePath = Constants.CECCR_USER_BASE_PATH + userName + "/" + jobName + "/";
    }

    @PostConstruct
    private void init() {
        selectedPredictors = new ArrayList<>();
        String[] selectedPredictorIdArray = selectedPredictorIds.split("\\s+");

        for (String selectedPredictorId : selectedPredictorIdArray) {
            Predictor p = predictorRepository.findOne(Long.parseLong(selectedPredictorId));
            selectedPredictors.add(p);
        }
        Collections.sort(selectedPredictors, new Comparator<Predictor>() {
            public int compare(Predictor p1, Predictor p2) {
                return p1.getId().compareTo(p2.getId());
            }
        });

        if (wasRecovered) {
            this.predictionDataset = datasetRepository.findOne(prediction.getDatasetId());
            if (predictionDataset.getSdfFile() != null) {
                this.sdf = predictionDataset.getSdfFile();
            }
        }
    }

    private static PredictionValue createPredObject(String[] extValues) {

        if (extValues == null) {
            return null;
        }
        int arraySize = extValues.length;

        PredictionValue predOutput = new PredictionValue();
        predOutput.setCompoundName(extValues[0]);
        try {
            predOutput.setNumModelsUsed(Integer.parseInt(extValues[1]));
            predOutput.setPredictedValue(Float.parseFloat(extValues[2]));
            if (arraySize > 3) {
                predOutput.setStandardDeviation(Float.parseFloat(extValues[3]));
            }
        } catch (Exception ex) {
            // if it couldn't get the information, then there is no prediction
            // for this compound.
            // Don't worry about the NumberFormatException, it doesn't matter.
        }

        return predOutput;

    }

    public static List<PredictionValue> parsePredOutput(String fileLocation, Long predictorId) throws IOException {
        logger.debug("Reading prediction output from " + fileLocation);
        List<PredictionValue> allPredValue = new ArrayList<>();
        try {
            BufferedReader in = new BufferedReader(new FileReader(fileLocation));
            String inputString;

            // skip all the non-blank lines with junk in them
            while (!(inputString = in.readLine()).equals("")) {
                ;
            }
            // now skip some blank lines
            while ((inputString = in.readLine()).equals("")) {
                ;
            }
            // now we're at the data we need
            do {
                String[] predValues = inputString.split("\\s+");
                PredictionValue extValOutput = createPredObject(predValues);
                extValOutput.setPredictorId(predictorId);
                allPredValue.add(extValOutput);
            } while ((inputString = in.readLine()) != null);
            in.close();
        } catch (Exception ex) {
            logger.error("", ex);
            ;
        }

        return allPredValue;
    }

    public String getProgress(String userName) {

        try {
            if (!step.equals(Constants.PREDICTING)) {
                return step;
            } else {
                // get the % done of the overall prediction

                if (allPredsTotalModels < 0) {
                    // we haven't read the needed predictor data yet
                    // get the number of models in all predictors, and their
                    // names
                    allPredsTotalModels = 0;
                    String[] selectedPredictorIdArray = selectedPredictorIds.split("\\s+");
                    List<String> selectedPredictorIds = Lists.newArrayList(Arrays.asList(selectedPredictorIdArray));
                    Collections.sort(selectedPredictorIds);
                    for (int i = 0; i < selectedPredictorIds.size(); i++) {
                        Predictor sp =
                                predictorRepository.findOne(Long.parseLong(selectedPredictorIds.get(i)));

                        if (sp.getChildType() != null && sp.getChildType().equals(Constants.NFOLD)) {
                            String[] childIds = sp.getChildIds().split("\\s+");
                            for (String childId : childIds) {
                                Predictor cp = predictorRepository.findOne(Long.parseLong(childId));
                                allPredsTotalModels += cp.getNumTestModels();
                                selectedPredictorNames.add(sp.getName() + "/" + cp.getName());
                            }
                        } else {
                            allPredsTotalModels += sp.getNumTestModels();
                        }

                        selectedPredictorNames.add(sp.getName());
                    }
                }

                float modelsPredictedSoFar = 0;
                for (int i = 0; i < selectedPredictorNames.size(); i++) {

                    File predOutFile = null;

                    if (predictionDataset.getDatasetType().equals(Constants.PREDICTION) || predictionDataset
                            .getDatasetType().equals(Constants.MODELING)) {
                        predOutFile = new File(
                                filePath + selectedPredictorNames.get(i) + "/" + Constants.PRED_OUTPUT_FILE + "_vs_"
                                        + predictionDataset.getSdfFile().toLowerCase() + ".renorm.preds");
                    } else if (predictionDataset.getDatasetType().equals(Constants.PREDICTIONWITHDESCRIPTORS)
                            || predictionDataset.getDatasetType().equals(Constants.MODELINGWITHDESCRIPTORS)) {
                        predOutFile = new File(
                                filePath + selectedPredictorNames.get(i) + "/" + Constants.PRED_OUTPUT_FILE + "_vs_"
                                        + predictionDataset.getXFile().toLowerCase() + ".renorm.preds");
                    }

                    if (predOutFile.exists()) {
                        // quickly count the number of lines in the output
                        // file for this predictor
                        // there are 4 header lines
                        modelsPredictedSoFar +=
                                FileAndDirOperations.getNumLinesInFile(predOutFile.getAbsolutePath()) - 4;
                    } else {
                        // SVM will just have a bunch of files ending in
                        // ".pred". Count them to get progress.
                        try {
                            File dir = new File(filePath + selectedPredictorNames.get(i) + "/");
                            modelsPredictedSoFar += (dir.list(new FilenameFilter() {
                                public boolean accept(File arg0, String arg1) {
                                    return arg1.endsWith(".pred");
                                }
                            }).length);
                        } catch (Exception ex) {
                            // whatever...
                        }
                    }

                }
                if (allPredsTotalModels == 0) {
                    return Constants.PREDICTING; // missing database
                    // information, probably
                }
                float progress = modelsPredictedSoFar / allPredsTotalModels;
                progress *= 100; // it's a percent
                return step + " (" + Math.round(progress) + "%)";
            }

        } catch (Exception ex) {
            logger.error("User: " + userName + "Job: " + jobName + " " + ex);
            return "";
        }
    }

    public Long setUp() throws Exception {
        // create Prediction object in DB to allow for recovery of this job if
        // it fails.

        if (prediction == null) {
            prediction = new Prediction();
        }

        prediction.setDatabase(this.sdf);
        prediction.setUserName(this.userName);
        prediction.setSimilarityCutoff(Float.parseFloat(this.cutoff));
        prediction.setComputeZscore(Constants.NO);
        prediction.setPredictorIds(this.selectedPredictorIds);
        prediction.setName(this.jobName);
        prediction.setDatasetId(predictionDataset.getId());
        prediction.setHasBeenViewed(Constants.NO);
        prediction.setJobCompleted(Constants.NO);
        predictionRepository.save(prediction);
        lookupId = prediction.getId();
        jobType = Constants.PREDICTION;

        logger.debug("User: " + userName + "Job: " + jobName + " Setting up prediction task");
        try {
            new File(Constants.CECCR_USER_BASE_PATH + userName + "/" + jobName).mkdir();

            if (predictionDataset.getUserName().equals(userName)) {

                if (sdf != null && !sdf.trim().isEmpty()) {
                    FileAndDirOperations.copyFile(
                            Constants.CECCR_USER_BASE_PATH + userName + "/DATASETS/" + predictionDataset.getName() + "/"
                                    + sdf, Constants.CECCR_USER_BASE_PATH + userName + "/" + jobName + "/" + sdf);

                }else{
                    throw new RuntimeException("Cannot find SDF file for Dataset");
                }
                if (predictionDataset.getXFile() != null && !predictionDataset.getXFile().trim().isEmpty()) {
                    FileAndDirOperations.copyFile(
                            Constants.CECCR_USER_BASE_PATH + userName + "/DATASETS/" + predictionDataset.getName() + "/"
                                    + predictionDataset.getXFile(),
                            Constants.CECCR_USER_BASE_PATH + userName + "/" + jobName + "/" + predictionDataset
                                    .getXFile());
                }
                else{
                    throw new RuntimeException("Cannot find X file for Dataset");
                }
            } else {
                // public datasets always have SDFs ...msypa(8/30/2011)->not
                // always true
                if (sdf != null && !sdf.trim().isEmpty()) {
                    logger.debug("User: " + userName + " Job: " + jobName + " Copying file: " + (
                            Constants.CECCR_USER_BASE_PATH + "all-users" + "/DATASETS/" + predictionDataset.getName()
                                    + "/" + sdf) + " to the " + (Constants.CECCR_USER_BASE_PATH + userName + "/"
                            + jobName + "/" + sdf));
                    FileAndDirOperations.copyFile(
                            Constants.CECCR_USER_BASE_PATH + "all-users" + "/DATASETS/" + predictionDataset.getName()
                                    + "/" + sdf, Constants.CECCR_USER_BASE_PATH + userName + "/" + jobName + "/" + sdf);
                }
            }
        } catch (Exception e) {
            logger.error("User: " + userName + "Job: " + jobName + " " + e);
        }

        return lookupId;
    }

    public void preProcess() throws Exception {
        for (int i = 0; i < selectedPredictors.size(); i++) {
            // We're keeping a count of how many times each predictor was used.
            // So, increment number of times used on each and save each predictor object.
            Predictor selectedPredictor = selectedPredictors.get(i);
            selectedPredictor.setNumPredictions(selectedPredictor.getNumPredictions() + 1);
            predictorRepository.save(selectedPredictor);
        }

        // Now, make the prediction with each predictor.
        // First, copy dataset into jobDir.

        step = Constants.SETUP;
        CreateJobDirectories.createDirs(userName, jobName);

        String path = Constants.CECCR_USER_BASE_PATH + userName + "/" + jobName + "/";

        CopyJobFiles.getDatasetFiles(userName, predictionDataset, Constants.PREDICTION, path);

        if (jobList.equals(Constants.LSF)) {
            // move files out to LSF
        }
    }

    public String executeLSF() throws Exception {
        return "";
    }

    private List<PredictionValue> makePredictions(Predictor predictor, String sdfile, String basePath,
                                                  String datasetPath) throws Exception {

        List<PredictionValue> predValues = new ArrayList<>();
        String predictionDir = basePath + predictor.getName() + "/";
        List<Predictor> childPredictors = predictorRepository.findByParentId(predictor.getId());

        if (childPredictors.size() > 0) {
            // recurse. Call this function for each childPredictor (if there are any).
            List<List<PredictionValue>> childResults = new ArrayList<>();
            for (Predictor childPredictor : childPredictors) {
                List<PredictionValue> results = makePredictions(childPredictor, sdfile, predictionDir, datasetPath);
                if (results != null) {
                    childResults.add(results);
                }
            }
            if (childResults.size() == 0) {
                // this should never happen; there's a check to prevent people
                // from selecting predictors that have no usable models in their
                // child predictors
                throw new Exception("No child in the nfold predictor generated any results!");
            }

            // average the results from the child predictions and return them
            predValues = new ArrayList<>();

            List<PredictionValue> firstChildResults = childResults.get(0);
            for (PredictionValue pv : firstChildResults) {
                PredictionValue parentPredictionValue = new PredictionValue();
                parentPredictionValue.setCompoundName(pv.getCompoundName());
                parentPredictionValue.setNumModelsUsed(childResults.size());
                parentPredictionValue.setNumTotalModels(childResults.size());
                parentPredictionValue.setObservedValue(pv.getObservedValue());
                parentPredictionValue.setPredictorId(predictor.getId());
                parentPredictionValue.setZscore(pv.getZscore());
                predValues.add(parentPredictionValue);
            }
            // calculate average predicted value and stddev over each child
            for (int i = 0; i < firstChildResults.size(); i++) {
                SummaryStatistics compoundPredictedValues = new SummaryStatistics();
                for (List<PredictionValue> childResult : childResults) {
                    if (childResult.get(i).getPredictedValue() != null) {
                        compoundPredictedValues.addValue(childResult.get(i).getPredictedValue());
                    }
                }
                if (!Double.isNaN(compoundPredictedValues.getMean())) {
                    predValues.get(i).setPredictedValue((float) compoundPredictedValues.getMean());
                    predValues.get(i).setStandardDeviation((float) compoundPredictedValues.getStandardDeviation());
                }
            }

            // commit predValues to DB
            for (PredictionValue pv : predValues) {
                pv.setPredictionId(prediction.getId());
                predictionValueRepository.save(pv);
            }
            return predValues;
        } else {
            // no child predictors, so just make a prediction

            // 2. copy predictor into jobDir/predictorDir
            new File(predictionDir).mkdirs();

            step = Constants.COPYPREDICTOR;
            CopyJobFiles.getPredictorFiles(userName, predictor, predictionDir, true);

            // done with 2. (copy predictor into jobDir/predictorDir)

            // 3. copy dataset from jobDir to jobDir/predictorDir. Scale
            // descriptors to fit predictor.
            FileAndDirOperations.copyDirContents(datasetPath, predictionDir, false);

            if (predictor.getDescriptorGeneration().equals(Constants.UPLOADED)) {
                // the prediction descriptors file name is different if the
                // user provided a .x file.
                sdfile = predictionDataset.getXFile();
            }

            step = Constants.PROCDESCRIPTORS;

            if (predictor.getDescriptorGeneration().equals(Constants.ISIDA)) {
                DescriptorIsida descriptorIsida = new DescriptorIsida();
                descriptorIsida.generateIsidaDescriptorsWithHeader(predictionDir + sdfile,
                        predictionDir + sdfile + descriptorIsida.getFileRenormEnding(),
                        predictionDir + predictor.getSdFileName() + descriptorIsida.getFileHdrEnding());
            }
            DescriptorUtility
                    .convertDescriptorsToXAndScale(predictionDir, sdfile, "train_0.x", sdfile + ".renorm.x",
                            predictor.getDescriptorGeneration(), predictor.getScalingType(),
                            predictionDataset.getNumCompound());

            // done with 3. (copy dataset from jobDir to jobDir/predictorDir.
            // Scale descriptors to fit predictor.)

            // 4. make predictions in jobDir/predictorDir

            step = Constants.PREDICTING;
            logger.debug("User: " + userName + "Job: " + jobName + " ExecutePredictor: Making predictions");
            Path predictorDir = predictor.getDirectoryPath(predictorRepository);
            ScikitRandomForestPrediction scikitPred = new ScikitRandomForestPrediction();
            if (predictor.getModelMethod().equals(Constants.SVM)) {
                Svm.runSvmPrediction(predictionDir, sdfile + ".renorm.x");
            } else if (predictor.getModelMethod().equals(Constants.KNNGA) || predictor.getModelMethod()
                    .equals(Constants.KNNSA)) {
                KnnPlus.runKnnPlusPrediction(predictionDir, sdfile);
            } else if (predictor.getModelMethod().equals(Constants.RANDOMFOREST_R)) {
                LegacyRandomForest.runRandomForestPrediction(predictionDir, jobName, sdfile, predictor);
            } else if (predictor.getModelMethod().equals(Constants.RANDOMFOREST)) {
                scikitPred = RandomForest.predict(predictorDir, Paths.get(predictionDir), sdfile + ".renorm" + ".x");
            }
            // done with 4. (make predictions in jobDir/predictorDir)

            // 5. get output, put it into predictionValue objects and save
            // them

            step = Constants.READPRED;

            if (predictor.getModelMethod().equals(Constants.SVM)) {
                predValues = Svm.readPredictionOutput(predictionDir, sdfile + ".renorm.x", predictor.getId());
            } else if (predictor.getModelMethod().equals(Constants.KNNGA) || predictor.getModelMethod()
                    .equals(Constants.KNNSA)) {
                predValues = KnnPlus.readPredictionOutput(predictionDir, predictor.getId(), sdfile + ".renorm.x");
            } else if (predictor.getModelMethod().equals(Constants.RANDOMFOREST_R)) {
                predValues = LegacyRandomForest.readPredictionOutput(predictionDir, predictor.getId());
            } else if (predictor.getModelMethod().equals(Constants.RANDOMFOREST)) {
                Map<String, Double> predictions = scikitPred.getPredictions();
                for (String key : predictions.keySet()) {
                    PredictionValue pv = new PredictionValue();
                    pv.setPredictorId(predictor.getId());
                    pv.setCompoundName(key);
                    pv.setNumTotalModels(1);
                    pv.setNumModelsUsed(1);
                    pv.setPredictedValue((float) ((double) predictions.get(key)));
                    predValues.add(pv);
                }
            }


            //Apply applicability domain
            logger.info("apply applicability domain");
            String execstr = "";
            String predictionXFile = predictionDir + sdfile + ".renorm.x";
            File predictionFile = new File(predictionXFile);
            if (!predictionFile.exists()) {
                predictionXFile = predictionDir + "RF_" + sdfile + ".renorm.x";
            }

            String predictorXFile =
                    predictor.getModelMethod().startsWith(Constants.RANDOMFOREST) ? "RF_train_0.x" : "train_0.x";
            execstr = Constants.CECCR_BASE_PATH + "get_ad/get_ad64 " + predictionDir + predictorXFile + " " +
                    "-4PRED=" + predictionXFile + " -OUT=" + predictionDir + "PRE_AD";
            RunExternalProgram.runCommandAndLogOutput(execstr, predictionDir, "getAD");

            //Read AD results
//            if (!cutoff.equals("-1")) {
                try {

                    String gadFile = predictionDir + "PRE_AD.gad";
                    File file = new File(gadFile);
                    FileReader fin = new FileReader(file);
                    Scanner src = new Scanner(fin);
                    int counter = 0;

                    while (src.hasNext()) {
                        String readLine = src.nextLine();
                        if (readLine.startsWith("ID")) {
                            while (src.hasNext() && counter < predValues.size()) {
                                readLine = src.nextLine();
                                String[] values = readLine.split("\\s+");
                                String zScore = values[3];
                                predValues.get(counter).setZscore(Float.parseFloat(zScore));
                                counter++;
                            }
                        }
                    }

                    src.close();
                    fin.close();

                } catch (Exception e) {//Catch exception if any
                    logger.error("User: " + userName + "Job: " + jobName + " " + e);
                }
//            }

            for (PredictionValue pv : predValues) {
                pv.setPredictionId(prediction.getId());
                predictionValueRepository.save(pv);
            }

            // done with 5. (get output, put it into predictionValue objects
            // and save them)
            // TODO remove copied dataset and predictor; they are redundant
        }
        return predValues;
    }

    public void executeLocal() throws Exception {

        String path = Constants.CECCR_USER_BASE_PATH + userName + "/" + jobName + "/";
        String sdfile = predictionDataset.getSdfFile();

        // Workflow for this section will be:
        // for each predictor do {
        // 2. copy predictor into jobDir/predictorDir
        // 3. copy dataset from jobDir to jobDir/predictorDir. Scale
        // descriptors to fit predictor.
        // 4. make predictions in jobDir/predictorDir
        // 5. get output, put it into predictionValue objects and save them
        // }

        // this is gonna need some major changes if we ever want it to work
        // with LSF.
        // basically the workflow will need to be written into a shell script
        // that LSF can execute

        if (predictionDataset.getNumCompound() > 10000) {
            // We will probably run out of memory if we try to process this
            // job in Java.
            logger.warn("User: " + userName + "Job: " + jobName + " Prediction set too large!");
        }

        // for each predictor do {
        for (int i = 0; i < selectedPredictors.size(); i++) {
            Predictor predictor = selectedPredictors.get(i);
            makePredictions(predictor, sdfile, path, path);
        }

        // remove prediction dataset descriptors from prediction output dir;
        // they are not needed
        try {
            String[] baseDirFiles = new File(Constants.CECCR_USER_BASE_PATH + userName + "/" + jobName + "/").list();
            for (String fileName : baseDirFiles) {
                if (new File(Constants.CECCR_USER_BASE_PATH + userName + "/" + jobName + "/" + fileName).exists()) {
                    FileAndDirOperations
                            .deleteFile(Constants.CECCR_USER_BASE_PATH + userName + "/" + jobName + "/" + fileName);
                }
            }
        } catch (Exception ex) {
            logger.error("User: " + userName + "Job: " + jobName + " " + ex);
        }
    }

    // helpers below this point.

    public void postProcess() throws Exception {

        if (jobList.equals(Constants.LSF)) {
            // move files back from LSF
        }

        PredictionUtilities.MoveToPredictionsDir(userName, jobName);
        prediction.setJobCompleted(Constants.YES);
        prediction.setComputeZscore(Constants.YES);
        prediction.setStatus("saved");
        predictionRepository.save(prediction);
        File dir = new File(Constants.CECCR_USER_BASE_PATH + this.userName + "/" + this.jobName + "/");
        FileAndDirOperations.deleteDir(dir);
        WriteCsv.writePredictionValuesAsCSV(prediction.getId());
    }

    public void delete() throws Exception {

    }

    public String getStatus() {
        return step;
    }

    public void setStep(String step) {
        this.step = step;
    }

    public String getJobName() {
        return jobName;
    }

    public Dataset getPredictionDataset() {
        return predictionDataset;
    }

    public void setPredictionDataset(Dataset predictionDataset) {
        this.predictionDataset = predictionDataset;
    }

}
