package edu.unc.ceccr.chembench.actions;

import com.google.common.collect.Lists;
import com.opensymphony.xwork2.ActionContext;
import com.opensymphony.xwork2.ActionSupport;
import edu.unc.ceccr.chembench.global.Constants;
import edu.unc.ceccr.chembench.jobs.CentralDogma;
import edu.unc.ceccr.chembench.persistence.Dataset;
import edu.unc.ceccr.chembench.persistence.HibernateUtil;
import edu.unc.ceccr.chembench.persistence.Predictor;
import edu.unc.ceccr.chembench.persistence.User;
import edu.unc.ceccr.chembench.taskObjects.QsarPredictionTask;
import edu.unc.ceccr.chembench.utilities.PopulateDataObjects;
import edu.unc.ceccr.chembench.utilities.RunExternalProgram;
import edu.unc.ceccr.chembench.utilities.Utility;
import edu.unc.ceccr.chembench.workflows.descriptors.ReadDescriptors;
import edu.unc.ceccr.chembench.workflows.modelingPrediction.RunSmilesPrediction;
import org.apache.log4j.Logger;
import org.hibernate.Session;

import java.io.File;
import java.io.FileReader;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class PredictionAction extends ActionSupport {

    private static Logger logger = Logger.getLogger(PredictionAction.class.getName());

    private User user = User.getCurrentUser();
    private List<Predictor> selectedPredictors = Lists.newArrayList();
    private List<SmilesPrediction> smilesPredictions;
    private String smilesString;
    private String smilesCutoff;
    private Long selectedDatasetId;
    private String cutOff = "0.5";
    private String jobName;
    private String selectedPredictorIds;

    public String makeSmilesPrediction() throws Exception {
        String result = SUCCESS;
        ActionContext context = ActionContext.getContext();

        /* use the same session for all data requests */
        Session session = HibernateUtil.getSession();
        String smiles = ((String[]) context.getParameters().get("smiles"))[0];
        smilesString = smiles;
        String cutoff = ((String[]) context.getParameters().get("cutoff"))[0];
        smilesCutoff = cutoff;
        String predictorIds = ((String[]) context.getParameters().get("predictorIds"))[0];
        logger.debug(" 1: " + smiles + " 2: " + cutoff + " 3: " + predictorIds);
        logger.debug(user.getUserName());
        logger.debug("SMILES predids: " + predictorIds);
        String[] selectedPredictorIdArray = predictorIds.split("\\s+");
        List<Predictor> predictors = Lists.newArrayList();
        Set<String> descriptorTypes = new HashSet<String>();
        for (int i = 0; i < selectedPredictorIdArray.length; i++) {
            Predictor predictor =
                    PopulateDataObjects.getPredictorById(Long.parseLong(selectedPredictorIdArray[i]), session);
            String descriptorType = predictor.getDescriptorGeneration();
            // skip predictors with uploaded descriptors, since we can't
            // generate them for the SDF generated from the SMILES string
            if (!descriptorType.equals(Constants.UPLOADED)) {
                predictors.add(predictor);
                descriptorTypes.add(descriptorType);
            }

            if (predictor.getModelMethod().startsWith(Constants.KNN)) {
                logger.warn("SMILES prediction attempted on KNN predictor");
            }
        }
        /* we don't need the session again */
        session.close();

        /* stores results */
        smilesPredictions = Lists.newArrayList();
        int numPredictors = predictors.size();

        for (int i = 0; i < predictors.size(); i++) {
            Predictor predictor = predictors.get(i);
            String zScore = "";

            /* make smiles dir */
            Path baseSmilesDir = Paths.get(Constants.CECCR_USER_BASE_PATH, user.getUserName(), "SMILES");
            Files.createDirectories(baseSmilesDir);
            Path predPath = Files.createTempDirectory(baseSmilesDir, predictor.getName());
            String smilesDir = predPath.toString() + "/";
            logger.debug("Created the directory " + smilesDir);

            // generate an SDF from this SMILES string
            RunSmilesPrediction.smilesToSDF(smiles, smilesDir);
            logger.info(String.format("Generated SDF file from SMILES \"%s\" written to %s", smiles, smilesDir));
            // generate descriptors using the given SDF file except for ISIDA
            if (!predictor.getDescriptorGeneration().equals(Constants.ISIDA)) {
                RunSmilesPrediction.generateDescriptorsForSDF(smilesDir, descriptorTypes);
            }
            logger.info("Generated descriptors for SDF: " + descriptorTypes.toString());

            String[] predValues = new String[3];
            int totalModels = predictor.getNumTestModels();

            // for n-folded predictors
            if (predictor.getChildType() != null && predictor.getChildType().equals(Constants.NFOLD)) {
                Boolean computedAD = false;
                String[] ids = predictor.getChildIds().split("\\s+");
                logger.info("Predictor is n-folded.");
                List<String[]> tempPred = Lists.newArrayList();
                for (int j = 0; j < ids.length; j++) {
                    session = HibernateUtil.getSession();
                    Predictor tempP = PopulateDataObjects.getPredictorById(Long.parseLong(ids[j]), session);
                    session.close();

                    // since predictions are made per-fold, each fold needs
                    // access to the SDF file as well as any descriptor matrices
                    new File(smilesDir, tempP.getName()).mkdirs();
                    for (String s : new File(smilesDir).list()) {
                        if (!(new File(s)).isDirectory() && s.startsWith("smiles")) {
                            Path target = new File(smilesDir, s).toPath();
                            Path link = new File(new File(smilesDir, tempP.getName()), s).toPath();
                            try {
                                Files.createSymbolicLink(link, target);
                            } catch (FileAlreadyExistsException e) {
                                // pass
                            }
                        }
                    }

                    tempPred.add(RunSmilesPrediction
                            .PredictSmilesSDF(smilesDir + tempP.getName() + "/", user.getUserName(), tempP));

                    totalModels += tempP.getNumTestModels();
                    logger.debug("Calculating predictions for " + tempP.getName());

                    // Calculate applicability domain
                    if (!computedAD) {
                        String execstr = "";
                        execstr = Constants.CECCR_BASE_PATH + "get_ad/get_ad64 " + smilesDir + tempP.getName() +
                                "/train_0.x " + "-4PRED=" + smilesDir + tempP.getName() + "/smiles.sdf.renorm.x " + "" +
                                " -OUT=" + smilesDir + "smiles_AD";
                        RunExternalProgram.runCommandAndLogOutput(execstr, smilesDir, "getAD");
                        computedAD = true;

                        // Read AD results
                        try {
                            String gadFile = smilesDir + "smiles_AD.gad";
                            File file = new File(gadFile);
                            FileReader fin = new FileReader(file);
                            Scanner src = new Scanner(fin);

                            while (src.hasNext()) {
                                String readLine = src.nextLine();
                                if (readLine.startsWith("ID")) {
                                    readLine = src.nextLine();
                                    if (readLine.startsWith("0")) {
                                        String[] values = readLine.split("\\s+");
                                        zScore = values[3];
                                        break;
                                    }
                                }
                            }
                            src.close();
                            fin.close();
                        } catch (Exception e) {
                            logger.error("User: " + user + "SMILES: " + smiles, e);
                        }
                    }
                }

                int predictingModels = 0;
                double predictedValue = 0d;
                double standartDeviation = 0d;

                /* getting average values */
                for (String[] s : tempPred) {
                    predictingModels += Integer.parseInt(s[0]);
                    predictedValue += Double.parseDouble(s[1]);
                    try {
                        standartDeviation += Double.parseDouble(s[2]);
                    } catch (NumberFormatException e) {
                        // pass (e.g. if only one model, stddev is N/A)
                    }
                    // debug part
                    logger.debug("Predicting models: " + s[0]);
                    logger.debug("Predicted value: " + s[1]);
                    logger.debug("Standard deviation: " + s[2]);
                }
                predValues[0] = String.valueOf(predictingModels);
                predValues[1] = Utility.roundSignificantFigures(String.valueOf(predictedValue / ids.length),
                        Constants.REPORTED_SIGNIFICANT_FIGURES);
                predValues[2] = Utility.roundSignificantFigures(String.valueOf(standartDeviation / ids.length),
                        Constants.REPORTED_SIGNIFICANT_FIGURES);
            }
            /*
             * create descriptors for the SDF, normalize them , and make a
             * prediction
             */
            else {
                predValues = RunSmilesPrediction.PredictSmilesSDF(smilesDir, user.getUserName(), predictor);

                // Calculate applicability domian
                String execstr = "";
                execstr = Constants.CECCR_BASE_PATH + "get_ad/get_ad64 " + smilesDir + "/train_0.x " + "-4PRED=" +
                        smilesDir + "/smiles.sdf.renorm.x " + " -OUT=" + smilesDir + "smiles_AD";
                RunExternalProgram.runCommandAndLogOutput(execstr, smilesDir, "getAD");

                // Read AD results
                try {
                    String gadFile = smilesDir + "smiles_AD.gad";
                    File file = new File(gadFile);
                    FileReader fin = new FileReader(file);
                    Scanner src = new Scanner(fin);

                    while (src.hasNext()) {
                        String readLine = src.nextLine();
                        if (readLine.startsWith("ID")) {
                            readLine = src.nextLine();
                            if (readLine.startsWith("0")) {
                                String[] values = readLine.split("\\s+");
                                zScore = values[3];
                                break;
                            }
                        }
                    }
                    src.close();
                    fin.close();
                } catch (Exception e) {
                    logger.error("User: " + user + "SMILES: " + smiles, e);
                }
            }

            // read predValues and build the prediction output object
            SmilesPrediction sp = new SmilesPrediction();
            sp.setPredictorName(predictor.getName());
            sp.setTotalModels(totalModels);

            sp.setPredictingModels(Integer.parseInt(predValues[0]));
            sp.setPredictedValue(predValues[1]);
            sp.setStdDeviation(predValues[2]);
            sp.setZScore(zScore);

            // add it to the array
            smilesPredictions.add(sp);
        }

        logger.info("made SMILES prediction on string " + smiles + " with predictors " + predictorIds + " for " + user
                .getUserName());

        return result;
    }

    public String makeDatasetPrediction() throws Exception {
        /*
         * prediction form submitted, so create a new prediction task and run
         * it
         */
        /* use the same session for all data requests */
        Session session = HibernateUtil.getSession();

        Dataset predictionDataset = PopulateDataObjects.getDataSetById(selectedDatasetId, session);
        String sdf = predictionDataset.getSdfFile();

        if (jobName != null) {
            jobName = jobName.replaceAll(" ", "_");
            jobName = jobName.replaceAll("\\(", "_");
            jobName = jobName.replaceAll("\\)", "_");
            jobName = jobName.replaceAll("\\[", "_");
            jobName = jobName.replaceAll("\\]", "_");
            jobName = jobName.replaceAll("/", "_");
            jobName = jobName.replaceAll("&", "_");
        }

        logger.debug(user.getUserName());
        logger.debug("predids: " + selectedPredictorIds);

        QsarPredictionTask predTask =
                new QsarPredictionTask(user.getUserName(), jobName, sdf, cutOff, selectedPredictorIds,
                        predictionDataset);

        predTask.setUp();
        int numCompounds = predictionDataset.getNumCompound();
        String[] ids = selectedPredictorIds.split("\\s+");
        int numModels = 0;

        List<Predictor> selectedPredictors = Lists.newArrayList();

        for (int i = 0; i < ids.length; i++) {
            Predictor sp = PopulateDataObjects.getPredictorById(Long.parseLong(ids[i]), session);
            selectedPredictors.add(sp);
            if (sp.getChildType() != null && sp.getChildType().equals(Constants.NFOLD)) {
                String[] childIds = sp.getChildIds().split("\\s+");
                for (String childId : childIds) {
                    Predictor cp = PopulateDataObjects.getPredictorById(Long.parseLong(childId), session);
                    numModels += cp.getNumTestModels();
                }
            } else {
                numModels += sp.getNumTestModels();
            }
        }

        /*
         * check descriptors of each of the selected predictors. Make sure
         * that the prediction dataset contains all of those descriptors,
         * otherwise error out.
         */
        for (Predictor sp : selectedPredictors) {
            String[] predictionDatasetDescriptors = predictionDataset.getAvailableDescriptors().split("\\s+");

            boolean descriptorsMatch = false;

            if (sp.getDescriptorGeneration().equals(Constants.UPLOADED)) {
                // get the uploaded descriptors for the dataset
                String predictionXFile = predictionDataset.getXFile();

                String predictionDatasetDir =
                        Constants.CECCR_USER_BASE_PATH + predictionDataset.getUserName() + "/DATASETS/"
                                + predictionDataset.getName() + "/";
                if (predictionXFile != null && !predictionXFile.trim().isEmpty()) {

                    logger.debug("Staring to read predictors from file: " + predictionDatasetDir + predictionXFile);
                    String[] predictionDescs =
                            ReadDescriptors.readDescriptorNamesFromX(predictionXFile, predictionDatasetDir);

                    // get the uploaded descriptors for the predictor
                    Dataset predictorDataset = PopulateDataObjects.getDataSetById(sp.getDatasetId(), session);
                    String predictorDatasetDir =
                            Constants.CECCR_USER_BASE_PATH + predictorDataset.getUserName() + "/DATASETS/"
                                    + predictorDataset.getName() + "/";
                    String[] predictorDescs =
                            ReadDescriptors.readDescriptorNamesFromX(predictorDataset.getXFile(), predictorDatasetDir);

                    descriptorsMatch = true;
                    /*
                     * for each predictor desc, make sure there's a matching
                     * prediction desc.
                     */
                    for (int i = 0; i < predictorDescs.length; i++) {
                        boolean matchingDescriptor = false;
                        for (int j = 0; j < predictionDescs.length; j++) {
                            if (predictorDescs[i].equals(predictionDescs[j])) {
                                matchingDescriptor = true;
                                j = predictionDescs.length;
                            }
                        }
                        if (!matchingDescriptor) {
                            descriptorsMatch = false;
                            addActionError(
                                    "The predictor '" + sp.getName() + "' contains the descriptor '" + predictorDescs[i]
                                            + "', but this " + "descriptor was not found in "
                                            + "the prediction dataset.");
                        }
                    }

                    if (!descriptorsMatch) {
                        return ERROR;
                    }
                }
            } else {
                for (int i = 0; i < predictionDatasetDescriptors.length; i++) {
                    if (sp.getDescriptorGeneration().equals(Constants.MOLCONNZ) && predictionDatasetDescriptors[i]
                            .equals(Constants.MOLCONNZ)) {
                        descriptorsMatch = true;
                    } else if (sp.getDescriptorGeneration().equals(Constants.CDK) && predictionDatasetDescriptors[i]
                            .equals(Constants.CDK)) {
                        descriptorsMatch = true;
                    } else if (sp.getDescriptorGeneration().equals(Constants.DRAGONH) && predictionDatasetDescriptors[i]
                            .equals(Constants.DRAGONH)) {
                        descriptorsMatch = true;
                    } else if (sp.getDescriptorGeneration().equals(Constants.DRAGONNOH)
                            && predictionDatasetDescriptors[i].equals(Constants.DRAGONNOH)) {
                        descriptorsMatch = true;
                    } else if (sp.getDescriptorGeneration().equals(Constants.MOE2D) && predictionDatasetDescriptors[i]
                            .equals(Constants.MOE2D)) {
                        descriptorsMatch = true;
                    } else if (sp.getDescriptorGeneration().equals(Constants.MACCS) && predictionDatasetDescriptors[i]
                            .equals(Constants.MACCS)) {
                        descriptorsMatch = true;
                    } else if (sp.getDescriptorGeneration().equals(Constants.ISIDA) && predictionDatasetDescriptors[i]
                            .equals(Constants.ISIDA)) {
                        descriptorsMatch = true;
                    }
                }

                if (!descriptorsMatch) {
                    addActionError("The predictor '" + sp.getName() + "' is based on " + sp.getDescriptorGeneration()
                            + " descriptors, but the dataset '" + predictionDataset.getName()
                            + "' does not have these descriptors. " + "You will not be able to make"
                            + " this prediction.");
                    return ERROR;
                }
            }

        }

        CentralDogma centralDogma = CentralDogma.getInstance();
        String emailOnCompletion = "false";
        centralDogma.addJobToIncomingList(user.getUserName(), jobName, predTask, numCompounds, numModels,
                emailOnCompletion);

        logger.info("making prediction run on dataset " + predictionDataset.getName() + " with predictors "
                + selectedPredictorIds + " for " + user.getUserName());

        // give back the session at the end
        session.close();
        return SUCCESS;
    }

    public String execute() throws Exception {
        return SUCCESS;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<Predictor> getSelectedPredictors() {
        return selectedPredictors;
    }

    public void setSelectedPredictors(List<Predictor> selectedPredictors) {
        this.selectedPredictors = selectedPredictors;
    }

    public Long getSelectedDatasetId() {
        return selectedDatasetId;
    }

    public void setSelectedDatasetId(Long selectedDatasetId) {
        this.selectedDatasetId = selectedDatasetId;
    }

    public String getCutOff() {
        return cutOff;
    }

    public void setCutOff(String cutOff) {
        this.cutOff = cutOff;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getSelectedPredictorIds() {
        return selectedPredictorIds;
    }

    public void setSelectedPredictorIds(String selectedPredictorIds) {
        this.selectedPredictorIds = selectedPredictorIds;
    }

    public List<SmilesPrediction> getSmilesPredictions() {
        return smilesPredictions;
    }

    public void setSmilesPredictions(List<SmilesPrediction> smilesPredictions) {
        this.smilesPredictions = smilesPredictions;
    }

    public String getSmilesString() {
        return smilesString;
    }

    public void setSmilesString(String smilesString) {
        this.smilesString = smilesString;
    }

    public String getSmilesCutoff() {
        return smilesCutoff;
    }

    public void setSmilesCutoff(String smilesCutoff) {
        this.smilesCutoff = smilesCutoff;
    }

    public class SmilesPrediction {
        // used by makeSmilesPrediction()
        String predictedValue;
        String stdDeviation;
        String zScore;
        int predictingModels;
        int totalModels;
        String predictorName;

        public String getPredictedValue() {
            return predictedValue;
        }

        public void setPredictedValue(String predictedValue) {
            this.predictedValue = predictedValue;
        }

        public String getStdDeviation() {
            return stdDeviation;
        }

        public void setStdDeviation(String stdDeviation) {
            this.stdDeviation = stdDeviation;
        }

        public String getZScore() {
            return zScore;
        }

        public void setZScore(String zScore) {
            this.zScore = zScore;
        }

        public int getPredictingModels() {
            return predictingModels;
        }

        public void setPredictingModels(int predictingModels) {
            this.predictingModels = predictingModels;
        }

        public int getTotalModels() {
            return totalModels;
        }

        public void setTotalModels(int totalModels) {
            this.totalModels = totalModels;
        }

        public String getPredictorName() {
            return predictorName;
        }

        public void setPredictorName(String predictorName) {
            this.predictorName = predictorName;
        }
    }

}
