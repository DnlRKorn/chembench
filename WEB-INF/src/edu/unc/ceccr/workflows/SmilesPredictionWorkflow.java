package edu.unc.ceccr.workflows;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;

import edu.unc.ceccr.global.Constants;
import edu.unc.ceccr.persistence.Descriptors;
import edu.unc.ceccr.persistence.Predictor;
import edu.unc.ceccr.utilities.DatasetFileOperations;
import edu.unc.ceccr.utilities.FileAndDirOperations;
import edu.unc.ceccr.utilities.Utility;

public class SmilesPredictionWorkflow{
	public static String[] PredictSmilesSDF(String workingDir, String username, Predictor predictor, Float cutoff) throws Exception{

		String sdfile = workingDir + "smiles.sdf";
		Utility.writeToDebug("Running PredictSmilesSDF in dir " + workingDir);
		
		//copy the predictor to the workingDir.
		String predictorUsername = predictor.getUserName();
		if(predictorUsername.equalsIgnoreCase("_all")){
			predictorUsername = "all-users";	
		}
		String fromDir = Constants.CECCR_USER_BASE_PATH + predictorUsername + "/PREDICTORS/" + predictor.getName() + "/";
		
		//get train_0.x file from the predictor dir.
		Utility.writeToDebug("Copying predictor files from " + fromDir);
		GetJobFilesWorkflow.getPredictorFiles(username, predictor, workingDir);
		
		Utility.writeToDebug("Copying complete. Generating descriptors. ");
		
		//create the descriptors for the chemical and read them in
		ArrayList<String> descriptorNames = new ArrayList<String>();
		ArrayList<Descriptors> descriptorValueMatrix = new ArrayList<Descriptors>();
		ArrayList<String> chemicalNames = DatasetFileOperations.getSDFCompoundList(sdfile);

		if(predictor.getDescriptorGeneration().equals(Constants.MOLCONNZ)){
			GenerateDescriptorWorkflow.GenerateMolconnZDescriptors(sdfile, sdfile + ".molconnz");
			ReadDescriptorsFileWorkflow.readMolconnZDescriptors(sdfile + ".molconnz", descriptorNames, descriptorValueMatrix);
		}
		else if(predictor.getDescriptorGeneration().equals(Constants.DRAGONH)){
			GenerateDescriptorWorkflow.GenerateHExplicitDragonDescriptors(sdfile, sdfile + ".dragonH");
			ReadDescriptorsFileWorkflow.readDragonDescriptors(sdfile + ".dragonH", descriptorNames, descriptorValueMatrix);
		}
		else if(predictor.getDescriptorGeneration().equals(Constants.DRAGONNOH)){
			GenerateDescriptorWorkflow.GenerateHExplicitDragonDescriptors(sdfile, sdfile + ".dragonNoH");
			ReadDescriptorsFileWorkflow.readDragonDescriptors(sdfile + ".dragonNoH", descriptorNames, descriptorValueMatrix);
		}
		else if(predictor.getDescriptorGeneration().equals(Constants.MOE2D)){
			GenerateDescriptorWorkflow.GenerateMoe2DDescriptors(sdfile, sdfile + ".moe2D");
			ReadDescriptorsFileWorkflow.readMoe2DDescriptors(sdfile + ".moe2D", descriptorNames, descriptorValueMatrix);
		}
		else if(predictor.getDescriptorGeneration().equals(Constants.MACCS)){
			GenerateDescriptorWorkflow.GenerateMaccsDescriptors(sdfile, sdfile + ".maccs");
			ReadDescriptorsFileWorkflow.readMaccsDescriptors(sdfile + ".maccs", descriptorNames, descriptorValueMatrix);
		}

		Utility.writeToDebug("Normalizing descriptors to fit predictor.");

		String descriptorString = Utility.StringArrayListToString(descriptorNames);
		WriteDescriptorsFileWorkflow.writePredictionXFile(chemicalNames, descriptorValueMatrix, descriptorString, sdfile + ".renorm.x", workingDir + "train_0.x", predictor.getScalingType());

		//write a dummy .a file because knn+ needs it or it fails bizarrely... X_X
		String actfile = sdfile + ".renorm.a";
		BufferedWriter aout = new BufferedWriter(new FileWriter(actfile));
		aout.write("1 0");
		aout.close();
		
	    //Run prediction
		Utility.writeToDebug("Running prediction.");
		String preddir = workingDir;
		
		String execstr = "";
		if(predictor.getModelMethod().equals(Constants.KNN)){
			execstr = "knn+ knn-output.list -4PRED=" + "smiles.sdf.renorm.x" + " -AD=" + cutoff + "_avd -OUT=" + Constants.PRED_OUTPUT_FILE;
	    }
		else if(predictor.getModelMethod().equals(Constants.KNNGA) || 
				predictor.getModelMethod().equals(Constants.KNNSA)){
			execstr = "knn+ models.tbl -4PRED=" + "smiles.sdf.renorm.x" + " -AD=" + cutoff + "_avd -OUT=" + Constants.PRED_OUTPUT_FILE;
		}
		
		Utility.writeToDebug("Running external program: " + execstr + " in dir: " + preddir);
		Process p = Runtime.getRuntime().exec(execstr, null, new File(preddir));
		Utility.writeProgramLogfile(preddir, "PredActivCont3rwknnLIN", p.getInputStream(), p.getErrorStream());
		p.waitFor();
		
        //read prediction output
		String outputFile = Constants.PRED_OUTPUT_FILE + "_vs_smiles.sdf.renorm.preds";
		Utility.writeToDebug("Reading file: " + workingDir + outputFile);
		BufferedReader in = new BufferedReader(new FileReader(workingDir + outputFile));
		String inputString;
		
		//Skip the first four lines (header data)
		in.readLine();
		in.readLine();
		in.readLine();
		in.readLine();
		
		//get output for each model
		ArrayList<String> predValueArray = new ArrayList<String>();
		while ((inputString = in.readLine()) != null && ! inputString.equals("")){
			String[] predValues = inputString.split("\\s+");
			if(predValues!= null && predValues.length > 2 && ! predValues[2].equals("NA")){
				//Utility.writeToDebug(predValues[1] + " " + predValues[2]);
				predValueArray.add(predValues[2]);
			}
		}

		Utility.writeToDebug("numModels: " + predValueArray.size());
		
		double sum = 0;
		double mean = 0;
		if(predValueArray.size() > 0){
			for(String predValue : predValueArray){
				sum += Float.parseFloat(predValue);
			}
			mean = sum / predValueArray.size();
		}

		double stddev = 0;
		if(predValueArray.size() > 1){
			for(String predValue : predValueArray){
				double distFromMeanSquared = Math.pow((Double.parseDouble(predValue) - mean), 2);
				stddev += distFromMeanSquared;
			}
			//divide sum then take sqrt to get stddev
			stddev = Math.sqrt( stddev / predValueArray.size());
		}
			
		Utility.writeToDebug("prediction: " + mean);
		Utility.writeToDebug("stddev: " + stddev);

		//format numbers nicely and return them
		String[] prediction = new String[3];
		prediction[0] = "" + predValueArray.size();
		if(predValueArray.size() > 0){
			String predictedValue = DecimalFormat.getInstance().format(mean).replaceAll(",", "");
			Utility.writeToDebug("String-formatted prediction: " + predictedValue);
			predictedValue = (Utility.roundSignificantFigures(predictedValue, Constants.REPORTED_SIGNIFICANT_FIGURES));
			prediction[1] = predictedValue;
		}
		else{
			prediction[1] = "N/A - Cutoff Too Low";
		}
		if(predValueArray.size() > 1){
			String stdDevStr = DecimalFormat.getInstance().format(stddev).replaceAll(",", "");
			Utility.writeToDebug("String-formatted stddev: " + stdDevStr);
			stdDevStr = (Utility.roundSignificantFigures(stdDevStr, Constants.REPORTED_SIGNIFICANT_FIGURES));
			prediction[2] = stdDevStr;
		}
		else{
			prediction[2] = "N/A";
		}
		
	    return prediction;
	}
	
	public static void smilesToSDF(String smiles, String smilesDir) throws Exception{
		//takes in a SMILES string and produces an SDF file from it. 
		//Returns the file path as a string.
		
		Utility.writeToDebug("Running smilesToSDF with SMILES: " + smiles);
		
		//set up the directory, just in case it's not there yet.
		File dir = new File(smilesDir);
		dir.mkdirs();
		
		//write SMILES string to file
			FileWriter fstream = new FileWriter(smilesDir + "tmp.smiles");
	        BufferedWriter out = new BufferedWriter(fstream);
		    out.write(smiles + " 1");
		    out.close();
		
		    String sdfFileName = "smiles.sdf";
		   
		//execute molconvert to change it to SDF
	    	String execstr = "molconvert -2:O1 sdf " + smilesDir + "tmp.smiles -o " + smilesDir + sdfFileName;
	    	Utility.writeToDebug("Running external program: " + execstr);
	    	Process p = Runtime.getRuntime().exec(execstr);
	    	Utility.writeProgramLogfile(smilesDir, "molconvert", p.getInputStream(), p.getErrorStream());
	    	p.waitFor();
	    
    	//standardize the SDF	
			StandardizeMoleculesWorkflow.standardizeSdf(sdfFileName, sdfFileName + ".standardize", smilesDir);
			File standardized = new File(smilesDir + sdfFileName + ".standardize");
			if(standardized.exists()){
				//replace old SDF with new standardized SDF
				FileAndDirOperations.copyFile(smilesDir + sdfFileName + ".standardize", smilesDir + sdfFileName);
				FileAndDirOperations.deleteFile(smilesDir + sdfFileName + ".standardize");
			}
	    	
	    Utility.writeToDebug("Finished smilesToSDF");
	}	
}