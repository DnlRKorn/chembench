package edu.unc.ceccr.chembench.actions;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.opensymphony.xwork2.ActionContext;
import com.opensymphony.xwork2.ActionSupport;
import edu.unc.ceccr.chembench.global.Constants;
import edu.unc.ceccr.chembench.jobs.CentralDogma;
import edu.unc.ceccr.chembench.persistence.*;
import edu.unc.ceccr.chembench.taskObjects.CreateDatasetTask;
import edu.unc.ceccr.chembench.utilities.Utility;
import edu.unc.ceccr.chembench.workflows.datasets.DatasetFileOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DatasetAction extends ActionSupport {

    private static final Logger logger = LoggerFactory.getLogger(DatasetAction.class);
    private final DatasetRepository datasetRepository;
    private final PredictorRepository predictorRepository;
    private final PredictionRepository predictionRepository;
    private final JobRepository jobRepository;
    private String datasetName = "";
    private String datasetType = Constants.MODELING;
    private long id;
    private double modi;
    private String splitType = Constants.NFOLD;
    private String dataTypeModeling = Constants.CONTINUOUS;
    private String dataTypeModDesc = Constants.NONE;
    private String dataSetDescription = "";
    private String externalCompoundList = "";
    private String useActivityBinning = "true";
    private String numExternalCompounds = "20";
    private String externalCompoundsCountOrPercent = "percent";
    private String standardizeModeling = "true";
    private String standardizeModDesc = "true";
    private String standardizePredDesc = "true";
    private String standardizePrediction = "true";
    private String paperReference = "";
    private String descriptorTypeModDesc = "";
    private String descriptorTypePredDesc = "";
    private String hasBeenScaled = "false";
    private String useActivityBinningNFold = "true";
    private String numExternalFolds = "5";
    private String generateImagesM = "true";
    private String generateImagesP = "true";
    private String generateImagesPWD = "false";
    private String generateImagesMWD = "false";
    //file upload stuff
    //modeling
    private File sdfFileModeling = null;
    private String sdfFileModelingContentType = "";
    private String sdfFileModelingFileName = "";
    private File actFileModeling = null;
    private String actFileModelingContentType = "";
    private String actFileModelingFileName = "";
    //prediction
    private File sdfFilePrediction = null;
    private String sdfFilePredictionContentType = "";
    private String sdfFilePredictionFileName = "";
    //modeling with descriptors
    private File actFileModDesc = null;
    private String actFileModDescContentType = "";
    private String actFileModDescFileName = "";
    private File descriptorXModDesc = null;
    private String descriptorXModDescContentType = "";
    private String descriptorXModDescFileName = "";
    private File sdfFileModDesc = null;
    private String sdfFileModDescContentType = "";
    private String sdfFileModDescFileName = "";
    //prediction with descriptors
    private File xFilePredDesc = null;
    private String xFilePredDescContentType = "";
    private String xFilePredDescFileName = "";
    private File sdfFilePredDesc = null;
    private String sdfFilePredDescContentType = "";
    private String sdfFilePredDescFileName = "";
    //====== variables used for display on the JSP =====//
    private User user = User.getCurrentUser();
    private List<String> userDatasetNames;
    private List<String> userPredictorNames;
    private List<String> userPredictionNames;
    private List<String> userTaskNames;
    private List<String> userUploadedDescriptorTypes;
    private List<Predictor> userPredictorList;
    private String selectedDescriptorUsedName = "";
    private String descriptorNewName = "";
    private String selectedDescriptorUsedNameD = "";
    private String descriptorNewNameD = "";

    @Autowired
    public DatasetAction(DatasetRepository datasetRepository, PredictorRepository predictorRepository,
                         PredictionRepository predictionRepository, JobRepository jobRepository) {
        this.datasetRepository = datasetRepository;
        this.predictorRepository = predictorRepository;
        this.predictionRepository = predictionRepository;
        this.jobRepository = jobRepository;
    }

    private List<String> getUploadedDescriptorTypes() {
        Set<String> uploadedTypes = Sets.newHashSet();
        List<Dataset> datasets = datasetRepository.findByUserName(user.getUserName());
        datasets.addAll(datasetRepository.findAllPublicDatasets());
        for (Dataset d : datasets) {
            String uploadedDescriptorType = d.getUploadedDescriptorType();
            if (uploadedDescriptorType != null && !uploadedDescriptorType.isEmpty()) {
                uploadedTypes.add(uploadedDescriptorType);
            }
        }
        return Lists.newArrayList(uploadedTypes);
    }

    public String loadPage() throws Exception {
        //set up any values that need to be populated onto the page (dropdowns, lists, display stuff)
        List<Dataset> datasets = datasetRepository.findByUserName(user.getUserName());
        datasets.addAll(datasetRepository.findAllPublicDatasets());
        userDatasetNames = Lists.transform(datasets, Utility.NAME_TRANSFORM);
        userPredictorList = predictorRepository.findByUserName(user.getUserName());
        userPredictorNames = Lists.transform(userPredictorList, Utility.NAME_TRANSFORM);
        userPredictionNames =
                Lists.transform(predictionRepository.findByUserName(user.getUserName()), Utility.NAME_TRANSFORM);
        userTaskNames = Lists.transform(jobRepository.findByUserName(user.getUserName()), Utility.NAME_TRANSFORM);
        userUploadedDescriptorTypes = getUploadedDescriptorTypes();
        return SUCCESS;
    }

    public String execute() throws Exception {

        String emailOnCompletion = "false"; //for now

        String result = INPUT;

        ActionContext context = ActionContext.getContext();
        String userName = user.getUserName();
        if (datasetName != null) {
            datasetName = datasetName.replaceAll("\\s+", "_");
            datasetName = datasetName.replaceAll("\\(", "_");
            datasetName = datasetName.replaceAll("\\)", "_");
            datasetName = datasetName.replaceAll("\\[", "_");
            datasetName = datasetName.replaceAll("\\]", "_");
            datasetName = datasetName.replaceAll("/", "_");
            datasetName = datasetName.replaceAll("&", "_");
        }

        logger.debug("Starting dataset task");
        logger.debug("Uploaded dataset " + datasetName + " User: " + userName);

        List<String> msgs = new ArrayList<>();

        if (externalCompoundsCountOrPercent.equalsIgnoreCase("percent")) {
            double tmp = Double.parseDouble(numExternalCompounds);
            tmp /= 100;
            numExternalCompounds = "" + tmp;
        }
        if (splitType.equals(Constants.NFOLD)) {
            useActivityBinning = useActivityBinningNFold;
        }

        if (datasetType.equalsIgnoreCase(Constants.MODELING)) {
            //do file check
            if (sdfFileModeling == null && actFileModeling == null) {
                addActionError("File upload failed or no files supplied. If you are using Chrome, "
                        + "try again in a different browser such as Firefox.");
                result = ERROR;
            } else if (sdfFileModeling == null) {
                addActionError("Missing SDF or file upload error.");
                result = ERROR;
            } else if (actFileModeling == null) {
                addActionError("Missing Activity file or file upload error. If you do not have an Activity file for"
                        + " this dataset, use the Prediction Set option when uploading.");
                result = ERROR;
            }

            if (result.equalsIgnoreCase(INPUT)) {
                //verify uploaded files and copy them to the dataset dir
                if (actFileModelingFileName.endsWith(".a")) {
                    actFileModelingFileName =
                            actFileModelingFileName.substring(0, actFileModelingFileName.length() - 2) + ".act";
                } else if (!actFileModelingFileName.endsWith(".act")) {
                    actFileModelingFileName += ".act";
                }
                try {
                    msgs = DatasetFileOperations
                            .uploadDataset(userName, sdfFileModeling, sdfFileModelingFileName, actFileModeling,
                                    actFileModelingFileName, null, "", datasetName, dataTypeModeling, datasetType,
                                    externalCompoundList);
                    sdfFileModelingFileName =
                            sdfFileModelingFileName.replaceAll(" ", "_").replaceAll("\\(", "_").replaceAll("\\)", "_");
                    actFileModelingFileName =
                            actFileModelingFileName.replaceAll(" ", "_").replaceAll("\\(", "_").replaceAll("\\)", "_");
                } catch (Exception ex) {
                    logger.error("", ex);
                    result = ERROR;
                    msgs.add("An exception occurred while uploading this dataset: " + ex.getMessage());
                }
                if (!msgs.isEmpty()) {
                    for (String msg : msgs) {
                        addActionError(msg);
                    }
                    result = ERROR;
                }

                // if all is well so far, make sure that the dataset has at
                // least the minimum number of compounds that we require
                // (if it's a modeling dataset; we allow prediction datasets to
                // be any size)
                if (result != ERROR && datasetType.startsWith("MODELING")) {
                    int numCompounds = DatasetFileOperations.getACTCompoundNames(
                            Constants.CECCR_USER_BASE_PATH + userName + "/DATASETS/" + datasetName + "/" +
                                    actFileModelingFileName).size();
                    if (numCompounds < Constants.DATASET_MIN_COMPOUNDS) {
                        logger.warn(String.format("Rejected dataset job: had only %d compounds while %d are required",
                                numCompounds, Constants.DATASET_MIN_COMPOUNDS));
                        addActionError(String
                                .format("Your dataset job only has %d compounds while a minimum of %d are required. "
                                        + "Please choose a SDF and ACT file pair with a greater number of " +
                                        "compounds.", numCompounds, Constants.DATASET_MIN_COMPOUNDS));
                        result = ERROR;
                    }
                }
            }

            // proceed if no errors so far
            if (result.equalsIgnoreCase(INPUT)) {
                CreateDatasetTask datasetTask = new CreateDatasetTask(userName, datasetType,
                        //MODELING, PREDICTION, MODELINGWITHDESCRIPTORS, or PREDICTIONWITHDESCRIPTORS
                        sdfFileModelingFileName, //sdfFileName
                        actFileModelingFileName, //actFileName
                        "", //xFileName
                        "", //descriptor type, if datasetType is MODELINGWITHDESCRIPTORS or PREDICTIONWITHDESCRIPTORS
                        dataTypeModeling, //act file type, Continuous or Category,
                        // if datasetType is MODELING or MODELINGWITHDESCRIPTORS. Prediction otherwise.
                        standardizeModeling, //used in MODELING and PREDICTION
                        splitType, //RANDOM or USERDEFINED
                        "", //scaling type
                        numExternalCompounds, //if splitType is RANDOM
                        numExternalFolds, //if splitType is NFOLD
                        useActivityBinning, //if splitType is RANDOM
                        externalCompoundList, //if splitType is USERDEFINED
                        datasetName, paperReference, dataSetDescription, generateImagesM);
                try {
                    logger.debug("getting ACT compound count from " + Constants.CECCR_USER_BASE_PATH + userName +
                            "/DATASETS/" + datasetName + "/" + actFileModelingFileName);
                    int numCompounds = DatasetFileOperations.getACTCompoundNames(
                            Constants.CECCR_USER_BASE_PATH + userName + "/DATASETS/" + datasetName + "/" +
                                    actFileModelingFileName).size();
                    int numModels = 0;

                    CentralDogma centralDogma = CentralDogma.getInstance();
                    centralDogma.addJobToIncomingList(userName, datasetName, datasetTask, numCompounds, numModels,
                            emailOnCompletion);

                } catch (Exception ex) {
                    logger.error("", ex);
                }
            }
        } else if (datasetType.equalsIgnoreCase(Constants.PREDICTION)) {
            logger.debug("got into function");
            //do file check
            if (sdfFilePrediction == null) {
                addActionError("File upload failed or no files supplied. If you are using Chrome, "
                        + "try again in a different browser such as Firefox.");
                result = ERROR;
            }

            if (result.equalsIgnoreCase(INPUT)) {
                //verify uploaded files and copy them to the dataset dir
                try {
                    msgs = DatasetFileOperations
                            .uploadDataset(userName, sdfFilePrediction, sdfFilePredictionFileName, null, "", null, "",
                                    datasetName, dataTypeModeling, datasetType, externalCompoundList);
                    sdfFilePredictionFileName = sdfFilePredictionFileName.replaceAll(" ", "_").replaceAll("\\(", "_")
                            .replaceAll("\\)", "_");
                } catch (Exception ex) {
                    logger.error("", ex);
                    result = ERROR;
                    msgs.add("An exception occurred while uploading this dataset: " + ex.getMessage());
                }

                if (!msgs.isEmpty()) {
                    for (String msg : msgs) {
                        addActionError(msg);
                    }
                    result = ERROR;
                }
            }
            if (result.equalsIgnoreCase(INPUT)) {
                try {
                    CreateDatasetTask datasetTask = new CreateDatasetTask(userName, datasetType,
                            //MODELING, PREDICTION, MODELINGWITHDESCRIPTORS, or PREDICTIONWITHDESCRIPTORS
                            sdfFilePredictionFileName, //sdfFileName
                            "", //actFileName
                            "", //xFileName
                            "", //descriptor type, if datasetType is MODELINGWITHDESCRIPTORS or
                            // PREDICTIONWITHDESCRIPTORS
                            Constants.PREDICTION, //act file type, Continuous or Category,
                            // if datasetType is MODELING or MODELINGWITHDESCRIPTORS. Prediction otherwise.
                            standardizePrediction, //used in MODELING and PREDICTION
                            splitType, //RANDOM or USERDEFINED
                            "", //scaling type
                            numExternalCompounds, //if splitType is RANDOM
                            numExternalFolds, //if splitType is NFOLD
                            useActivityBinning, //if splitType is RANDOM
                            externalCompoundList, //if splitType is USERDEFINED
                            datasetName, paperReference, dataSetDescription, generateImagesP);

                    int numCompounds = DatasetFileOperations.getSdfCompoundNames(
                            Constants.CECCR_USER_BASE_PATH + userName + "/DATASETS/" + datasetName + "/" +
                                    sdfFilePredictionFileName).size();
                    int numModels = 0;
                    logger.debug("adding task");

                    CentralDogma centralDogma = CentralDogma.getInstance();
                    centralDogma.addJobToIncomingList(userName, datasetName, datasetTask, numCompounds, numModels,
                            emailOnCompletion);

                } catch (Exception ex) {
                    logger.error("", ex);
                    result = ERROR;
                    msgs.add("An exception occurred while creating this dataset: " + ex.getMessage());
                }
            }
        } else if (datasetType.equalsIgnoreCase(Constants.MODELINGWITHDESCRIPTORS)) {

            if (descriptorXModDesc == null || actFileModDesc == null) {
                addActionError("File upload failed or no files supplied. If you are using Chrome, "
                        + "try again in a different browser such as Firefox.");
                result = ERROR;
            }

            if (result.equalsIgnoreCase(INPUT)) {
                //verify uploaded files and copy them to the dataset dir
                try {
                    actFileModDescFileName =
                            actFileModDescFileName.replaceAll(" ", "_").replaceAll("\\(", "_").replaceAll("\\)", "_");
                    if (actFileModDescFileName.endsWith(".a")) {
                        actFileModDescFileName =
                                actFileModDescFileName.substring(0, actFileModDescFileName.length() - 2) + ".act";
                    } else if (!actFileModDescFileName.endsWith(".act")) {
                        actFileModDescFileName += ".act";
                    }
                    if (!descriptorXModDescFileName.endsWith(".x")) {
                        descriptorXModDescFileName += ".x";
                    }

                    msgs = DatasetFileOperations
                            .uploadDataset(userName, sdfFileModDesc, sdfFileModDescFileName, actFileModDesc,
                                    actFileModDescFileName, descriptorXModDesc, descriptorXModDescFileName, datasetName,
                                    dataTypeModeling, datasetType, externalCompoundList);
                    sdfFileModDescFileName =
                            sdfFileModDescFileName.replaceAll(" ", "_").replaceAll("\\(", "_").replaceAll("\\)", "_");
                    actFileModDescFileName =
                            actFileModDescFileName.replaceAll(" ", "_").replaceAll("\\(", "_").replaceAll("\\)", "_");
                    descriptorXModDescFileName =
                            descriptorXModDescFileName.replaceAll(" ", "_").replaceAll("\\(", "_").replaceAll("\\)", "_");
                    descriptorTypeModDesc =
                            descriptorNewName.trim().isEmpty() ? selectedDescriptorUsedName : descriptorNewName;

                } catch (Exception ex) {
                    logger.error("", ex);
                    result = ERROR;
                    msgs.add("An exception occurred while uploading this dataset: " + ex.getMessage());
                }

                if (!msgs.isEmpty()) {
                    for (String msg : msgs) {
                        addActionError(msg);
                    }
                    result = ERROR;
                }
            }
            if (result.equalsIgnoreCase(INPUT)) {
                try {
                    CreateDatasetTask datasetTask = new CreateDatasetTask(userName, datasetType,
                            //MODELING, PREDICTION, MODELINGWITHDESCRIPTORS, or PREDICTIONWITHDESCRIPTORS
                            sdfFileModDescFileName, //sdfFileName
                            actFileModDescFileName, //actFileName
                            descriptorXModDescFileName, //xFileName
                            descriptorTypeModDesc, //descriptor type, if datasetType is MODELINGWITHDESCRIPTORS or
                            // PREDICTIONWITHDESCRIPTORS
                            dataTypeModDesc, //act file type, Continuous or Category,
                            // if datasetType is MODELING or MODELINGWITHDESCRIPTORS. Prediction otherwise.
                            standardizeModDesc, splitType, //RANDOM or USERDEFINED
                            hasBeenScaled, //true or false
                            numExternalCompounds, //if splitType is RANDOM
                            numExternalFolds, //if splitType is NFOLD
                            useActivityBinning, //if splitType is RANDOM
                            externalCompoundList, //if splitType is USERDEFINED
                            datasetName, paperReference, dataSetDescription, generateImagesMWD);

                    int numCompounds = DatasetFileOperations.getACTCompoundNames(
                            Constants.CECCR_USER_BASE_PATH + userName + "/DATASETS/" + datasetName + "/" +
                                    actFileModDescFileName).size();
                    int numModels = 0;

                    CentralDogma centralDogma = CentralDogma.getInstance();
                    centralDogma.addJobToIncomingList(userName, datasetName, datasetTask, numCompounds, numModels,
                            emailOnCompletion);
                } catch (Exception ex) {
                    logger.error("", ex);
                    result = ERROR;
                    msgs.add("An exception occurred while creating this dataset: " + ex.getMessage());
                }
            }
        } else if (datasetType.equalsIgnoreCase(Constants.PREDICTIONWITHDESCRIPTORS)) {
            if (xFilePredDesc == null) {
                addActionError("File upload failed or no files supplied. If you are using Chrome, "
                        + "try again in a different browser such as Firefox.");
                result = ERROR;
            }

            if (result.equalsIgnoreCase(INPUT)) {
                //verify uploaded files and copy them to the dataset dir
                try {
                    if (!xFilePredDescFileName.endsWith(".x")) {
                        xFilePredDescFileName += ".x";
                    }

                    msgs = DatasetFileOperations
                            .uploadDataset(userName, sdfFilePredDesc, sdfFilePredDescFileName, null, "", xFilePredDesc,
                                    xFilePredDescFileName, datasetName, dataTypeModeling, datasetType,
                                    externalCompoundList);
                    sdfFilePredDescFileName =
                            sdfFilePredDescFileName.replaceAll(" ", "_").replaceAll("\\(", "_").replaceAll("\\)", "_");
                    xFilePredDescFileName =
                            xFilePredDescFileName.replaceAll(" ", "_").replaceAll("\\(", "_").replaceAll("\\)", "_");
                    descriptorTypePredDesc =
                            descriptorNewNameD.trim().isEmpty() ? selectedDescriptorUsedNameD : descriptorNewNameD;
                } catch (Exception ex) {
                    logger.error("", ex);
                    result = ERROR;
                    msgs.add("An exception occurred while uploading this dataset: " + ex.getMessage());
                }

                if (!msgs.isEmpty()) {
                    for (String msg : msgs) {
                        addActionError(msg);
                    }
                    result = ERROR;
                }
            }

            if (result.equalsIgnoreCase(INPUT)) {
                try {
                    CreateDatasetTask datasetTask = new CreateDatasetTask(userName, datasetType,
                            //MODELING, PREDICTION, MODELINGWITHDESCRIPTORS, or PREDICTIONWITHDESCRIPTORS
                            sdfFilePredDescFileName, //sdfFileName
                            "", //actFileName
                            xFilePredDescFileName, //xFileName
                            descriptorTypePredDesc, //descriptor type, if datasetType is MODELINGWITHDESCRIPTORS or
                            // PREDICTIONWITHDESCRIPTORS
                            Constants.PREDICTION, //act file type, Continuous or Category,
                            // if datasetType is MODELING or MODELINGWITHDESCRIPTORS. Prediction otherwise.
                            standardizePredDesc, splitType, //RANDOM or USERDEFINED
                            "", //hasBeenScaled
                            numExternalCompounds, //if splitType is RANDOM
                            numExternalFolds, //if splitType is NFOLD
                            useActivityBinning, //if splitType is RANDOM
                            externalCompoundList, //if splitType is USERDEFINED
                            datasetName, paperReference, dataSetDescription, generateImagesPWD);

                    int numCompounds = DatasetFileOperations.getXCompoundNames(
                            Constants.CECCR_USER_BASE_PATH + userName + "/DATASETS/" + datasetName + "/" +
                                    xFilePredDescFileName).size();
                    int numModels = 0;

                    CentralDogma centralDogma = CentralDogma.getInstance();
                    centralDogma.addJobToIncomingList(userName, datasetName, datasetTask, numCompounds, numModels,
                            emailOnCompletion);

                    //Queue.getInstance().addJob(datasetTask, userName, datasetName, numCompounds, numModels);
                } catch (Exception ex) {
                    logger.error("", ex);
                    result = ERROR;
                    msgs.add("An exception occurred while creating this dataset: " + ex.getMessage());
                }
            }
        }

        return result;
    }

    public String generateModi() throws Exception {
        Dataset dataset = datasetRepository.findOne(id);
        if (dataset == null || !dataset.canGenerateModi()) {
            return "badrequest";
        } else {
            if (!dataset.isModiGenerated()) {
                dataset.generateModi();
            }
            datasetRepository.save(dataset);
            this.modi = dataset.getModi();
            return SUCCESS;
        }
    }

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public String getDatasetType() {
        return datasetType;
    }

    public void setDatasetType(String datasetType) {
        this.datasetType = datasetType;
    }

    public String getSplitType() {
        return splitType;
    }

    public void setSplitType(String splitType) {
        this.splitType = splitType;
    }

    public String getDataTypeModeling() {
        return dataTypeModeling;
    }

    public void setDataTypeModeling(String dataTypeModeling) {
        this.dataTypeModeling = dataTypeModeling;
    }

    public String getDataTypeModDesc() {
        return dataTypeModDesc;
    }

    public void setDataTypeModDesc(String dataTypeModDesc) {
        this.dataTypeModDesc = dataTypeModDesc;
    }

    public String getDataSetDescription() {
        return dataSetDescription;
    }

    public void setDataSetDescription(String dataSetDescription) {
        this.dataSetDescription = dataSetDescription;
    }

    public String getExternalCompoundList() {
        return externalCompoundList;
    }

    public void setExternalCompoundList(String externalCompoundList) {
        this.externalCompoundList = externalCompoundList;
    }

    public String getUseActivityBinning() {
        return useActivityBinning;
    }

    public void setUseActivityBinning(String useActivityBinning) {
        this.useActivityBinning = useActivityBinning;
    }

    public String getNumExternalCompounds() {
        return numExternalCompounds;
    }

    public void setNumExternalCompounds(String numExternalCompounds) {
        this.numExternalCompounds = numExternalCompounds;
    }

    public String getStandardizeModeling() {
        return standardizeModeling;
    }

    public void setStandardizeModeling(String standardizeModeling) {
        this.standardizeModeling = standardizeModeling;
    }

    public String getStandardizeModDesc() {
        return standardizeModDesc;
    }

    public void setStandardizeModDesc(String standardizeModDesc) {
        this.standardizeModDesc = standardizeModDesc;
    }

    public String getStandardizePredDesc() {
        return standardizePredDesc;
    }

    public void setStandardizePredDesc(String standardizePredDesc) {
        this.standardizePredDesc = standardizePredDesc;
    }

    public String getStandardizePrediction() {
        return standardizePrediction;
    }

    public void setStandardizePrediction(String standardizePrediction) {
        this.standardizePrediction = standardizePrediction;
    }

    public String getPaperReference() {
        return paperReference;
    }

    public void setPaperReference(String paperReference) {
        this.paperReference = paperReference;
    }

    public String getDescriptorTypeModDesc() {
        return descriptorTypeModDesc;
    }

    public void setDescriptorTypeModDesc(String descriptorTypeModDesc) {
        this.descriptorTypeModDesc = descriptorTypeModDesc;
    }

    public String getDescriptorTypePredDesc() {
        return descriptorTypePredDesc;
    }

    public void setDescriptorTypePredDesc(String descriptorTypePredDesc) {
        this.descriptorTypePredDesc = descriptorTypePredDesc;
    }

    public String getGenerateImagesM() {
        return generateImagesM;
    }

    public void setGenerateImagesM(String generateImagesM) {
        this.generateImagesM = generateImagesM;
    }

    public String getGenerateImagesP() {
        return generateImagesP;
    }

    public void setGenerateImagesP(String generateImagesP) {
        this.generateImagesP = generateImagesP;
    }

    public String getGenerateImagesPWD() {
        return generateImagesPWD;
    }

    public void setGenerateImagesPWD(String generateImagesPWD) {
        this.generateImagesPWD = generateImagesPWD;
    }

    public String getGenerateImagesMWD() {
        return generateImagesMWD;
    }

    public void setGenerateImagesMWD(String generateImagesMWD) {
        this.generateImagesMWD = generateImagesMWD;
    }

    public File getSdfFileModeling() {
        return sdfFileModeling;
    }

    public void setSdfFileModeling(File sdfFileModeling) {
        this.sdfFileModeling = sdfFileModeling;
    }

    public String getSdfFileModelingContentType() {
        return sdfFileModelingContentType;
    }

    public void setSdfFileModelingContentType(String sdfFileModelingContentType) {
        this.sdfFileModelingContentType = sdfFileModelingContentType;
    }

    public String getSdfFileModelingFileName() {
        return sdfFileModelingFileName;
    }

    public void setSdfFileModelingFileName(String sdfFileModelingFileName) {
        this.sdfFileModelingFileName = sdfFileModelingFileName;
    }

    public File getSdfFileModDesc() {
        return sdfFileModDesc;
    }

    public void setSdfFileModDesc(File sdfFileModDesc) {
        this.sdfFileModDesc = sdfFileModDesc;
    }

    public String getSdfFileModDescContentType() {
        return sdfFileModDescContentType;
    }

    public void setSdfFileModDescContentType(String sdfFileModDescContentType) {
        this.sdfFileModDescContentType = sdfFileModDescContentType;
    }

    public String getSdfFileModDescFileName() {
        return sdfFileModDescFileName;
    }

    public void setSdfFileModDescFileName(String sdfFileModDescFileName) {
        this.sdfFileModDescFileName = sdfFileModDescFileName;
    }

    public File getSdfFilePrediction() {
        return sdfFilePrediction;
    }

    public void setSdfFilePrediction(File sdfFilePrediction) {
        this.sdfFilePrediction = sdfFilePrediction;
    }

    public String getSdfFilePredictionContentType() {
        return sdfFilePredictionContentType;
    }

    public void setSdfFilePredictionContentType(String sdfFilePredictionContentType) {
        this.sdfFilePredictionContentType = sdfFilePredictionContentType;
    }

    public String getSdfFilePredictionFileName() {
        return sdfFilePredictionFileName;
    }

    public void setSdfFilePredictionFileName(String sdfFilePredictionFileName) {
        this.sdfFilePredictionFileName = sdfFilePredictionFileName;
    }

    public File getActFileModeling() {
        return actFileModeling;
    }

    public void setActFileModeling(File actFileModeling) {
        this.actFileModeling = actFileModeling;
    }

    public String getActFileModelingContentType() {
        return actFileModelingContentType;
    }

    public void setActFileModelingContentType(String actFileModelingContentType) {
        this.actFileModelingContentType = actFileModelingContentType;
    }

    public String getActFileModelingFileName() {
        return actFileModelingFileName;
    }

    public void setActFileModelingFileName(String actFileModelingFileName) {
        this.actFileModelingFileName = actFileModelingFileName;
    }

    public File getActFileModDesc() {
        return actFileModDesc;
    }

    public void setActFileModDesc(File actFileModDesc) {
        this.actFileModDesc = actFileModDesc;
    }

    public String getActFileModDescContentType() {
        return actFileModDescContentType;
    }

    public void setActFileModDescContentType(String actFileModDescContentType) {
        this.actFileModDescContentType = actFileModDescContentType;
    }

    public String getActFileModDescFileName() {
        return actFileModDescFileName;
    }

    public void setActFileModDescFileName(String actFileModDescFileName) {
        this.actFileModDescFileName = actFileModDescFileName;
    }

    public File getDescriptorXModDesc() {
        return descriptorXModDesc;
    }

    public void setDescriptorXModDesc(File descriptorXModDesc) {
        this.descriptorXModDesc = descriptorXModDesc;
    }

    public String getDescriptorXModDescContentType() {
        return descriptorXModDescContentType;
    }

    public void setDescriptorXModDescContentType(String fileContentType) {
        descriptorXModDescContentType = fileContentType;
    }

    public String getDescriptorXModDescFileName() {
        return descriptorXModDescFileName;
    }

    public void setDescriptorXModDescFileName(String fileName) {
        descriptorXModDescFileName = fileName;
    }

    //end file upload stuff

    public File getXFilePredDesc() {
        return xFilePredDesc;
    }

    public void setXFilePredDesc(File filePredDesc) {
        xFilePredDesc = filePredDesc;
    }

    public String getXFilePredDescContentType() {
        return xFilePredDescContentType;
    }

    public void setXFilePredDescContentType(String filePredDescContentType) {
        xFilePredDescContentType = filePredDescContentType;
    }

    public String getXFilePredDescFileName() {
        return xFilePredDescFileName;
    }

    public void setXFilePredDescFileName(String filePredDescFileName) {
        xFilePredDescFileName = filePredDescFileName;
    }

    public File getSdfFilePredDesc() {
        return sdfFilePredDesc;
    }

    public void setSdfFilePredDesc(File sdfFilePredDesc) {
        this.sdfFilePredDesc = sdfFilePredDesc;
    }

    public String getSdfFilePredDescContentType() {
        return sdfFilePredDescContentType;
    }

    public void setSdfFilePredDescContentType(String sdfFilePredDescContentType) {
        this.sdfFilePredDescContentType = sdfFilePredDescContentType;
    }

    public String getSdfFilePredDescFileName() {
        return sdfFilePredDescFileName;
    }

    public void setSdfFilePredDescFileName(String sdfFilePredDescFileName) {
        this.sdfFilePredDescFileName = sdfFilePredDescFileName;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<String> getUserDatasetNames() {
        return userDatasetNames;
    }

    public void setUserDatasetNames(List<String> userDatasetNames) {
        this.userDatasetNames = userDatasetNames;
    }

    public List<String> getUserPredictorNames() {
        return userPredictorNames;
    }

    public void setUserPredictorNames(List<String> userPredictorNames) {
        this.userPredictorNames = userPredictorNames;
    }

    public List<String> getUserPredictionNames() {
        return userPredictionNames;
    }

    public void setUserPredictionNames(List<String> userPredictionNames) {
        this.userPredictionNames = userPredictionNames;
    }

    public List<String> getUserTaskNames() {
        return userTaskNames;
    }

    public void setUserTaskNames(List<String> userTaskNames) {
        this.userTaskNames = userTaskNames;
    }


    public List<Predictor> getUserPredictorList() {
        return userPredictorList;
    }

    public void setUserPredictorList(List<Predictor> userPredictorList) {
        this.userPredictorList = userPredictorList;
    }

    public String getExternalCompoundsCountOrPercent() {
        return externalCompoundsCountOrPercent;
    }

    public void setExternalCompoundsCountOrPercent(String externalCompoundsCountOrPercent) {
        this.externalCompoundsCountOrPercent = externalCompoundsCountOrPercent;
    }

    public String getUseActivityBinningNFold() {
        return useActivityBinningNFold;
    }

    public void setUseActivityBinningNFold(String useActivityBinningNFold) {
        this.useActivityBinningNFold = useActivityBinningNFold;
    }

    public String getNumExternalFolds() {
        return numExternalFolds;
    }

    public void setNumExternalFolds(String numExternalFolds) {
        this.numExternalFolds = numExternalFolds;
    }

    public String getHasBeenScaled() {
        return hasBeenScaled;
    }

    public void setHasBeenScaled(String hasBeenScaled) {
        this.hasBeenScaled = hasBeenScaled;
    }

    public List<String> getUserUploadedDescriptorTypes() {
        return userUploadedDescriptorTypes;
    }

    public void setUserUploadedDescriptorTypes(List<String> userUploadedDescriptorTypes) {
        this.userUploadedDescriptorTypes = userUploadedDescriptorTypes;
    }

    public String getDescriptorNewName() {
        return descriptorNewName;
    }

    public void setDescriptorNewName(String descriptorNewName) {
        this.descriptorNewName = descriptorNewName;
    }

    public String getSelectedDescriptorUsedName() {
        return selectedDescriptorUsedName;
    }

    public void setSelectedDescriptorUsedName(String selectedDescriptorUsedName) {
        this.selectedDescriptorUsedName = selectedDescriptorUsedName;
    }

    public String getDescriptorNewNameD() {
        return descriptorNewNameD;
    }

    public void setDescriptorNewNameD(String descriptorNewName) {
        this.descriptorNewNameD = descriptorNewName;
    }

    public String getSelectedDescriptorUsedNameD() {
        return selectedDescriptorUsedNameD;
    }

    public void setSelectedDescriptorUsedNameD(String selectedDescriptorUsedName) {
        this.selectedDescriptorUsedNameD = selectedDescriptorUsedName;
    }

    public double getModi() {
        return modi;
    }

    public void setModi(double modi) {
        this.modi = modi;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }


}
