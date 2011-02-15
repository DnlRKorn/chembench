package edu.unc.ceccr.action.ViewPredictor;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

//struts2
import com.opensymphony.xwork2.ActionSupport; 
import com.opensymphony.xwork2.ActionContext; 

import org.apache.struts.upload.FormFile;
import org.apache.struts2.interceptor.SessionAware;
import org.hibernate.Session;
import org.hibernate.Transaction;

import edu.unc.ceccr.global.Constants;
import edu.unc.ceccr.persistence.DataSet;
import edu.unc.ceccr.persistence.ExternalValidation;
import edu.unc.ceccr.persistence.HibernateUtil;
import edu.unc.ceccr.persistence.KnnModel;
import edu.unc.ceccr.persistence.KnnParameters;
import edu.unc.ceccr.persistence.KnnPlusModel;
import edu.unc.ceccr.persistence.KnnPlusParameters;
import edu.unc.ceccr.persistence.Prediction;
import edu.unc.ceccr.persistence.Predictor;
import edu.unc.ceccr.persistence.RandomForestGrove;
import edu.unc.ceccr.persistence.RandomForestParameters;
import edu.unc.ceccr.persistence.RandomForestTree;
import edu.unc.ceccr.persistence.SvmModel;
import edu.unc.ceccr.persistence.SvmParameters;
import edu.unc.ceccr.persistence.User;
import edu.unc.ceccr.utilities.PopulateDataObjects;
import edu.unc.ceccr.utilities.Utility;

public class ParametersPage extends ViewPredictorAction {
	private KnnParameters knnParameters;
	private KnnPlusParameters knnPlusParameters;
	private SvmParameters svmParameters;
	private RandomForestParameters randomForestParameters;
	
	public String load() throws Exception {
		getBasicParameters();
		
		String result = SUCCESS;
	
		if(selectedPredictor.getModelMethod().equals(Constants.RANDOMFOREST)){
			randomForestParameters = PopulateDataObjects.getRandomForestParametersById(selectedPredictor.getModelingParametersId(), session);
		}
		else if(selectedPredictor.getModelMethod().equals(Constants.KNNGA) || 
				selectedPredictor.getModelMethod().equals(Constants.KNNSA)){
			knnPlusParameters = PopulateDataObjects.getKnnPlusParametersById(selectedPredictor.getModelingParametersId(), session);
		}
		else if(selectedPredictor.getModelMethod().equals(Constants.KNN)){
			knnParameters = PopulateDataObjects.getKnnParametersById(selectedPredictor.getModelingParametersId(), session);
		}
		else if(selectedPredictor.getModelMethod().equals(Constants.SVM)){
			svmParameters = PopulateDataObjects.getSvmParametersById(selectedPredictor.getModelingParametersId(), session);
			if(svmParameters.getSvmTypeCategory().equals("0")){
				svmParameters.setSvmTypeCategory("C-SVC");
			}
			else{
				svmParameters.setSvmTypeCategory("nu-SVC");
			}
			if(svmParameters.getSvmTypeCategory().equals("3")){
				svmParameters.setSvmTypeCategory("epsilon-SVR");
			}
			else{
				svmParameters.setSvmTypeCategory("nu-SVR");
			}
			if(svmParameters.getSvmKernel().equals("0")){
				svmParameters.setSvmKernel("linear");
			}
			else if(svmParameters.getSvmKernel().equals("1")){
				svmParameters.setSvmKernel("polynomial");
			}
			else if(svmParameters.getSvmKernel().equals("2")){
				svmParameters.setSvmKernel("radial basis function");
			}
			else if(svmParameters.getSvmKernel().equals("3")){
				svmParameters.setSvmKernel("sigmoid");
			}
			if(svmParameters.getSvmHeuristics().equals("0")){
				svmParameters.setSvmHeuristics("NO");
			}
			else{
				svmParameters.setSvmHeuristics("YES");
			}
			if(svmParameters.getSvmProbability().equals("0")){
				svmParameters.setSvmProbability("NO");
			}
			else{
				svmParameters.setSvmProbability("YES");
			}
		}
		return result;
	}

	//getters and setters

	public KnnParameters getKnnParameters() {
		return knnParameters;
	}
	public void setKnnParameters(KnnParameters knnParameters) {
		this.knnParameters = knnParameters;
	}

	public KnnPlusParameters getKnnPlusParameters() {
		return knnPlusParameters;
	}
	public void setKnnPlusParameters(KnnPlusParameters knnPlusParameters) {
		this.knnPlusParameters = knnPlusParameters;
	}

	public SvmParameters getSvmParameters() {
		return svmParameters;
	}
	public void setSvmParameters(SvmParameters svmParameters) {
		this.svmParameters = svmParameters;
	}

	public RandomForestParameters getRandomForestParameters() {
		return randomForestParameters;
	}
	public void setRandomForestParameters(
			RandomForestParameters randomForestParameters) {
		this.randomForestParameters = randomForestParameters;
	}

	//end getters and setters
	
}