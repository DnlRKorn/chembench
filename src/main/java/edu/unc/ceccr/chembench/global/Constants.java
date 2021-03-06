package edu.unc.ceccr.chembench.global;

import java.util.ArrayList;
import java.util.List;

public class Constants {

    public enum ActivityType {
        CATEGORY, CONTINUOUS
    }

    public enum ScalingType {
        AUTOSCALING, RANGESCALING, NOSCALING
    }

    public static final String[] DESCRIPTORSETS = new String[]{Constants.CDK, Constants.DRAGONH,
            Constants.DRAGONNOH, Constants.MOE2D, Constants.MACCS, Constants.ISIDA, Constants.DRAGON7 };

    //After submitting around 250 jobs, LSF won't let you submit more, it just returns an error.
    //Cap the number of jobs that can be added to the LSF queue at any given time.
    public static final int MAXLSFJOBS = 200;
    public static final int REPORTED_SIGNIFICANT_FIGURES = 4;
    //How long a login will last if idle, measured in seconds. 21600 seconds = 6 hours.
    //Descriptor constants
    public static final int NUM_MACCS_KEYS = 400;
    //In practice, there are only 2 different types of MACCS keys -- 166 and 320.
    //We use the 166 version. Padding doesn't hurt, though -- the extra 0's get removed later.

    //Enums for Data Types
    public static final String CONTINUOUS = "CONTINUOUS";
    public static final String CATEGORY = "CATEGORY";
    public static final String INCOMING = "INCOMING";
    public static final String LSF = "LSF";

    //Type strings
    //The point of having these is to define capitalizations
    //(prevents case-sensitivity bugs)
    //and to define all possible settings for various objects.
    public static final String LOCAL = "LOCAL";
    public static final String ERROR = "ERROR";
    public static final String QUEUED = "QUEUED";
    public static final String PREPROC = "PREPROC";
    public static final String RUNNING = "RUNNING";
    public static final String POSTPROC = "POSTPROC";
    public static final String LSFWAIT = "LSFWAIT";
    public static final String NONE = "NONE";
    public static final String SOME = "SOME";
    public static final String TOP10 = "TOP10";
    public static final String TOP25 = "TOP25";
    public static final String TOP100 = "TOP100";
    public static final String TEN = "10";
    public static final String TWENTYFIVE = "25";
    public static final String FIFTY = "50";
    public static final String ONEHUNDRED = "100";
    public static final String ALL = "ALL";
    public static final String YES = "YES";
    public static final String NO = "NO";
    public static final String PRIVATE = "Private";
    public static final String HIDDEN = "Hidden";
    public static final String ADME = "ADME";
    public static final String TOXICITY = "Toxicity";
    public static final String DRUGDISCOVERY = "DrugDiscovery";
    public static final String DATASET = "DATASET";
    public static final String MODELING = "MODELING";
    public static final String PREDICTION = "PREDICTION";
    public static final String MODELINGWITHDESCRIPTORS = "MODELINGWITHDESCRIPTORS";
    public static final String PREDICTIONWITHDESCRIPTORS = "PREDICTIONWITHDESCRIPTORS";
    public static final String INVERSEOFSIZE = "INVERSEOFSIZE";
    public static final String NOWEIGHTING = "NOWEIGHTING";
    public static final String DRAGONH = "DRAGONH";
    public static final String DRAGONNOH = "DRAGONNOH";
    public static final String DRAGON7 = "DRAGON7";
    public static final String MOE2D = "MOE2D";
    public static final String MACCS = "MACCS";
    public static final String CDK = "CDK";
    public static final String ISIDA = "ISIDA";
    public static final String UPLOADED = "UPLOADED";
    public static final String KNN = "KNN";
    public static final String KNNSA = "KNN-SA";
    public static final String KNNGA = "KNN-GA";
    public static final String KNNPLUS = "KNNPLUS";
    public static final String SVM = "SVM";
    public static final String RANDOMFOREST = "RANDOMFOREST";
    public static final String RANDOMFOREST_R = "RANDOMFOREST-R";
    public static final String SIMULATEDANNEALING = "SIMULATEDANNEALING";
    public static final String GENETICALGORITHM = "GENETICALGORITHM";
    public static final String AUTOSCALING = "AUTOSCALING";
    public static final String RANGESCALING = "RANGESCALING";
    public static final String NOSCALING = "NOSCALING";
    public static final String RANDOM = "RANDOM";
    public static final String SPHEREEXCLUSION = "SPHEREEXCLUSION";
    public static final String USERDEFINED = "USERDEFINED";
    public static final String NFOLD = "NFOLD";
    //steps in dataset task
    public static final String SETUP = "Waiting in queue";
    public static final String VISUALIZATION = "Generating visualizations";
    public static final String SKETCHES = "Generating compound sketches";
    public static final String SPLITDATA = "Splitting Data";
    public static final String MODI = "Calculating modelability index";
    //steps in modeling task
    //also uses SETUP and SPLITDATA
    public static final String STANDARDIZING = "Standardizing compounds";
    public static final String DESCRIPTORS = "Generating descriptors";
    public static final String CHECKDESCRIPTORS = "Checking descriptors";
    public static final String PROCDESCRIPTORS = "Processing descriptors";
    public static final String YRANDOMSETUP = "Y-Randomization setup";
    public static final String MODELS = "Generating models";
    public static final String PREDEXT = "Predicting external set";
    public static final String READING = "Reading output files";
    //steps in prediction task
    //also uses DESCRIPTORS and SETUP
    public static final String COPYPREDICTOR = "Copying predictor";
    public static final String PREDICTING = "Predicting dataset";
    public static final String READPRED = "Reading predictions";
    //kNN Constants
    public static final int CONTINUOUS_NNN_LOCATION = 1;
    public static final int CONTINUOUS_Q_SQUARED_LOCATION = 2;
    public static final int CONTINUOUS_N_LOCATION = 3;
    public static final int CONTINUOUS_R_LOCATION = 8;
    public static final int CONTINUOUS_R_SQUARED_LOCATION = 9;
    public static final int CONTINUOUS_K1_LOCATION = 14;
    public static final int CONTINUOUS_K2_LOCATION = 15;
    public static final int CONTINUOUS_R01_SQUARED_LOCATION = 16;
    public static final int CONTINUOUS_R02_SQUARED_LOCATION = 17;
    //kNN Constants
    //nnn, Training Accuracy, Normalized Training Accuracy, Test Accuracy, and Normalized Test Accuracy.
    public static final int CATEGORY_NNN_LOCATION = 2;
    public static final int CATEGORY_TRAINING_ACC_LOCATION = 3;
    public static final int CATEGORY_NORMALIZED_TRAINING_ACC_LOCATION = 4;
    public static final int CATEGORY_TEST_ACC_LOCATION = 8;
    public static final int CATEGORY_NORMALIZED_TEST_ACC_LOCATION = 9;
    //External Validation Constants
    public static final int COMPOUND_ID = 0;
    //Following constants decreased by 1. Wonder what this will do...
    public static final int STRUCTURE_FILE = 0;
    public static final int ACTUAL = 1;
    public static final int PREDICTED = 2;
    public static final int NUM_MODELS = 3;
    public static final int STD_DEVIATION = 3;
    //for testing
    public static final Integer MAX_FILE_SIZE = new Integer("1024");
    public static final String ALL_USERS_USERNAME = "all-users";
    public static final String CCHEMBENCH = "cchembench";
    public static final String CTOXBENCH = "ctoxbench";
    public static final String MAINKNN = "MAINKNN";
    public static final String RANDOMKNN = "RANDOMKNN";
    //used by password hash function when user gets or changes their password
    public static final String SOURCE = "qwertyuio123NBV456pasdfghOPASDFGHjklm7890QWERTYUInbvcxzJKLMCXZ";
    public static final String VALIDATOR_STRING = "1234567890~!@#$%^&*()=+[]{}|:;'<>?";
    //a time in miliseconds the guest data is available, after that time will pass we'll delete guest data
    //3 hours for now
    public static final long GUEST_DATA_EXPIRATION_TIME = 10800000;
    // the minimum number of compounds required for a dataset job.
    // users submitting a job with fewer than this number will have their job
    // rejected and will see an error.
    public static final int DATASET_MIN_COMPOUNDS = 30;
    public static boolean doneReadingConfigFile = false;
    public static int SESSION_EXPIRATION_TIME = 21600;
    //file paths
    public static String LSFJOBPATH;
    public static String USERWORKFLOWSPATH;
    //Paths
    public static String CECCR_BASE_PATH;
    public static String TOMCAT_PATH;
    public static String CECCR_USER_BASE_PATH;
    public static String XML_FILE_PATH;
    public static String SDFILE_FILEPATH;
    public static String DATAFILE_FILEPATH;
    public static String IMAGE_FILEPATH;
    public static String SYSTEMCONFIG_XML_PATH;
    public static String CATEGORY_DATAFILE_FILEPATH;
    public static String CONTINUOUS_DATAFILE_FILEPATH;
    public static String EXECUTABLEFILE_PATH;
    public static String INSTALLS_PATH;
    public static String CDK_XMLFILE_PATH;
    public static String DRAGON7_SCRIPT_PATH;
    public static String SCRIPTS_PATH;
    public static String RF_BUILD_MODEL_RSCRIPT;
    public static String RF_PREDICT_RSCRIPT;
    public static String RF_DESCRIPTORS_USED_FILE;
    public static String KNN_OUTPUT_FILE;
    public static String EXTERNAL_VALIDATION_OUTPUT_FILE;
    public static String PRED_OUTPUT_FILE;
    public static String KNN_DEFAULT_FILENAME;
    public static String KNN_CATEGORY_DEFAULT_FILENAME;
    public static String SE_DEFAULT_FILENAME;
    public static String DESCRIPTOR_ERROR_FILE;
    public static String KNNPLUS_MODELS_FILENAME;
    public static String EXTERNAL_SET_A_FILE;
    public static String EXTERNAL_SET_X_FILE;
    public static String MODELING_SET_A_FILE;
    public static String MODELING_SET_X_FILE;
    //administration
    public static String WEBADDRESS;
    public static String WEBSITEEMAIL;
    public static List<String> ADMIN_LIST = new ArrayList<>();
    public static List<String> ADMINEMAIL_LIST = new ArrayList<>();
    public static List<String> DESCRIPTOR_DOWNLOAD_USERS_LIST = new ArrayList<>();
    // web service validation
    public static String RECAPTCHA_PUBLICKEY;
    public static String RECAPTCHA_PRIVATEKEY;
    public static int MAXCOMPOUNDS = 200;
    public static int MAXMODELS = 10000;
    public static String DATABASE_URL;
    public static String DATABASE_DRIVER;
    public static String DATABASE_USERNAME;
    public static String CECCR_DATABASE_NAME;
    public static String CECCR_DATABASE_PASSWORD;
    public static String WORKBENCH;

    // modelability index threshold
    public static double MODI_MODELABLE = 0.65;

}
