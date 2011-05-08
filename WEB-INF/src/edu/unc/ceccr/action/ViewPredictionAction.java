package edu.unc.ceccr.action;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
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
import org.hibernate.criterion.Order;

import edu.unc.ceccr.persistence.Compound;
import edu.unc.ceccr.global.Constants;
import edu.unc.ceccr.persistence.CompoundPredictions;
import edu.unc.ceccr.persistence.DataSet;
import edu.unc.ceccr.persistence.ExternalValidation;
import edu.unc.ceccr.persistence.HibernateUtil;
import edu.unc.ceccr.persistence.KnnModel;
import edu.unc.ceccr.persistence.Prediction;
import edu.unc.ceccr.persistence.PredictionValue;
import edu.unc.ceccr.persistence.Predictor;
import edu.unc.ceccr.persistence.User;
import edu.unc.ceccr.servlet.FileServlet;
import edu.unc.ceccr.taskObjects.QsarModelingTask;
import edu.unc.ceccr.utilities.DatasetFileOperations;
import edu.unc.ceccr.utilities.PopulateDataObjects;
import edu.unc.ceccr.utilities.Utility;
import edu.unc.ceccr.workflows.WriteDownloadableFilesWorkflow;

public class ViewPredictionAction extends ActionSupport {
	
	private User user;
	private String predictionId;
	private Prediction prediction;
	private List<Predictor> predictors; //put these in order by predictorId
	private DataSet dataset; //dataset used in prediction
	ArrayList<CompoundPredictions> compoundPredictionValues = new ArrayList<CompoundPredictions>();
	private String currentPageNumber;
	private String orderBy;
	private String sortDirection;
	private ArrayList<String> pageNums;
	
	public String loadPredictionsSection() throws Exception {
		Utility.writeToDebug("called loadPredictionsSection");
		
		String result = SUCCESS;
		//check that the user is logged in
		ActionContext context = ActionContext.getContext();

		Session session = HibernateUtil.getSession();

		if(context == null){
			Utility.writeToStrutsDebug("No ActionContext available");
		}
		else{
			user = (User) context.getSession().get("user");
			
			if(user == null){
				Utility.writeToStrutsDebug("No user is logged in.");
				result = LOGIN;
				return result;
			}
			if(context.getParameters().get("orderBy") != null){
				 orderBy = ((String[]) context.getParameters().get("orderBy"))[0];
			}
			else{
				orderBy = "compoundId";
			}
			if(context.getParameters().get("currentPageNumber") != null){
				currentPageNumber = ((String[]) context.getParameters().get("currentPageNumber"))[0]; 	
			}
			else{
				currentPageNumber = "1";
			}
			if(context.getParameters().get("predictionId") != null){
				predictionId = ((String[]) context.getParameters().get("predictionId"))[0]; 	
			}
			if(context.getParameters().get("sortDirection") != null){
				sortDirection = ((String[]) context.getParameters().get("sortDirection"))[0];
			}
			else{
				sortDirection = "asc";
			}
			
			//get prediction
			Utility.writeToStrutsDebug("prediction id: " + predictionId);
			prediction = PopulateDataObjects.getPredictionById(Long.parseLong(predictionId), session);
			prediction.setDatasetDisplay(PopulateDataObjects.getDataSetById(prediction.getDatasetId(), session).getName());
			if(predictionId == null){
				Utility.writeToStrutsDebug("Invalid prediction ID supplied.");
			}
			
			//get predictors for this prediction. Order them by predictor ID, increasing.
			predictors = new ArrayList<Predictor>();
			String[] predictorIds = prediction.getPredictorIds().split("\\s+");
			for(int i = 0; i < predictorIds.length; i++){
				predictors.add(PopulateDataObjects.getPredictorById(Long.parseLong(predictorIds[i]), session));
			}
			Collections.sort(predictors, new Comparator<Predictor>(){
				public int compare(Predictor p1, Predictor p2) {
		    		return p1.getId().compareTo(p2.getId());
			    }});

			//get dataset
			dataset = PopulateDataObjects.getDataSetById(prediction.getDatasetId(), session);
			
			//define which compounds will appear on page
			int pagenum, limit, offset;
			if(user.getViewPredictionCompoundsPerPage().equals(Constants.ALL)){
				pagenum = 0;
				limit = 99999999;
				offset = pagenum * limit;
			}
			else{
				pagenum = Integer.parseInt(currentPageNumber) - 1;
				limit = Integer.parseInt(user.getViewPredictionCompoundsPerPage()); //compounds per page to display
				offset = pagenum * limit; //which compoundid to start on
			}
			
			//get prediction values
			compoundPredictionValues = PopulateDataObjects.populateCompoundPredictionValues(prediction.getDatasetId(), Long.parseLong(predictionId), session);
			
			//sort the compoundPrediction array
			Utility.writeToDebug("Sorting compound predictions");
			if(orderBy == null || orderBy.equals("") || orderBy.equals("compoundId")){
				//sort by compoundId
				Collections.sort(compoundPredictionValues, new Comparator<CompoundPredictions>() {
				    public int compare(CompoundPredictions o1, CompoundPredictions o2) {
			    		return Utility.naturalSortCompare(o1.getCompound(), o2.getCompound());
				    }});
			}
			else if(orderBy != null && !orderBy.equals("")){
				//check if orderBy equals one of the predictor names,
				//and order by those values if so.
			
				for(int i = 0; i < predictors.size();i++){
					if(predictors.get(i).getName().equals(orderBy)){
						//tell each sub-object what its sortBy index is
						for(CompoundPredictions c : compoundPredictionValues){
							c.setSortByIndex(i);
						}
						Collections.sort(compoundPredictionValues, new Comparator<CompoundPredictions>() {
							public int compare(CompoundPredictions o1, CompoundPredictions o2) {
								float f1;
								float f2;
								if(o1.getPredictionValues().get(o1.getSortByIndex()).getPredictedValue() == null){
									return -1;
								}
								else{
									f1 = o1.getPredictionValues().get(o1.getSortByIndex()).getPredictedValue();
								}
								if(o2.getPredictionValues().get(o2.getSortByIndex()).getPredictedValue() == null){
									return 1;
								}
								else{
									f2 = o2.getPredictionValues().get(o2.getSortByIndex()).getPredictedValue();
								}
						    	return (f2 < f1? 1:-1);
						    }});
		    		}
				}
					
			}
			if(sortDirection != null && sortDirection.equals("desc")){
				Collections.reverse(compoundPredictionValues);
			}
			Utility.writeToDebug("Done sorting compound predictions");

			//displays the page numbers at the top
			pageNums = new ArrayList<String>(); 
			int j = 1;
			for(int i = 0; i < compoundPredictionValues.size(); i += limit){
				String page = Integer.toString(j);
				pageNums.add(page);
				j++;
			}
			
			//pick out the ones to be displayed on the page based on offset and limit
			int compoundNum = 0;
			for(int i = 0; i < compoundPredictionValues.size(); i++){
				if(compoundNum < offset || compoundNum >= (offset + limit)){
					//don't display this compound
					compoundPredictionValues.remove(i);
					i--;
				}				
				else{
					//leave it in the array
				}
				compoundNum++;
			}
		}
		return result;
	}

	public String loadWarningsSection() throws Exception {
		String result = SUCCESS;
		//check that the user is logged in
		ActionContext context = ActionContext.getContext();
		
		Session session = HibernateUtil.getSession();
		
		if(context == null){
			Utility.writeToStrutsDebug("No ActionContext available");
		}
		else{
			user = (User) context.getSession().get("user");
			
			if(user == null){
				Utility.writeToStrutsDebug("No user is logged in.");
				result = LOGIN;
				return result;
			}
			
			if(context.getParameters().get("predictionId") != null){
				predictionId = ((String[]) context.getParameters().get("predictionId"))[0]; 	
			}
			//get prediction
			Utility.writeToStrutsDebug("[ext_compounds] dataset id: " + predictionId);
			prediction = PopulateDataObjects.getPredictionById(Long.parseLong(predictionId), session);
			if(predictionId == null){
				Utility.writeToStrutsDebug("Invalid prediction ID supplied.");
			}
		}
		return result;
	}
	
	public String loadPage() throws Exception {

		String result = SUCCESS;
		
		//check that the user is logged in
		ActionContext context = ActionContext.getContext();

		Session session = HibernateUtil.getSession();
		
		if(context == null){
			Utility.writeToStrutsDebug("No ActionContext available");
		}
		else{
			user = (User) context.getSession().get("user");
			predictionId = ((String[]) context.getParameters().get("id"))[0];
			
			if(user == null){
				Utility.writeToStrutsDebug("No user is logged in.");
				result = LOGIN;
				return result;
			}

			if(context.getParameters().get("orderBy") != null){
				 orderBy = ((String[]) context.getParameters().get("orderBy"))[0];
			}
			String pagenumstr = null;
			if(context.getParameters().get("pagenum") != null){
				pagenumstr = ((String[]) context.getParameters().get("pagenum"))[0]; //how many to skip (pagination)
			}
			
			if(context.getParameters().get("predictionId") != null){
				predictionId = ((String[]) context.getParameters().get("predictionId"))[0]; 	
			}
			
			if(predictionId == null){
				Utility.writeToStrutsDebug("No prediction ID supplied.");
			}
			else{

				currentPageNumber = "1";
				if(pagenumstr != null){
					currentPageNumber = pagenumstr;
				}

				Utility.writeToStrutsDebug("prediction id: " + predictionId);
				prediction = PopulateDataObjects.getPredictionById(Long.parseLong(predictionId), session);
				prediction.setDatasetDisplay(PopulateDataObjects.getDataSetById(prediction.getDatasetId(), session).getName());
				if(predictionId == null){
					Utility.writeToStrutsDebug("Invalid prediction ID supplied.");
				}

				//get predictors for this prediction
				predictors = new ArrayList<Predictor>();
				String[] predictorIds = prediction.getPredictorIds().split("\\s+");
				for(int i = 0; i < predictorIds.length; i++){
					predictors.add(PopulateDataObjects.getPredictorById(Long.parseLong(predictorIds[i]), session));
				}

				//get dataset
				dataset = PopulateDataObjects.getDataSetById(prediction.getDatasetId(), session);

				//the prediction has now been viewed. Update DB accordingly.
				if(! prediction.getHasBeenViewed().equals(Constants.YES)){
					prediction.setHasBeenViewed(Constants.YES);
					Transaction tx = null;
					try {
						tx = session.beginTransaction();
						session.saveOrUpdate(prediction);
						tx.commit();
					} catch (RuntimeException e) {
						if (tx != null)
							tx.rollback();
						Utility.writeToDebug(e);
					}
				}
			}
		}

		session.close();
		
		//log the results
		if(result.equals(SUCCESS)){
			Utility.writeToStrutsDebug("Forwarding user " + user.getUserName() + " to viewPrediction page.");
		}
		else{
			Utility.writeToStrutsDebug("Cannot load page.");
		}
		
		return result;
	}
	
	public String getPredictionId() {
		return predictionId;
	}
	public void setPredictionId(String predictionId) {
		this.predictionId = predictionId;
	}

	public Prediction getPrediction() {
		return prediction;
	}
	public void setPrediction(Prediction prediction) {
		this.prediction = prediction;
	}

	public List<Predictor> getPredictors() {
		return predictors;
	}
	public void setPredictors(List<Predictor> predictors) {
		this.predictors = predictors;
	}
		
	public User getUser() {
		return user;
	}
	public void setUser(User user) {
		this.user = user;
	}

	public DataSet getDataset() {
		return dataset;
	}
	public void setDataset(DataSet dataset) {
		this.dataset = dataset;
	}
	
	public ArrayList<CompoundPredictions> getCompoundPredictionValues() {
		return compoundPredictionValues;
	}
	public void setCompoundPredictionValues(
			ArrayList<CompoundPredictions> compoundPredictionValues) {
		this.compoundPredictionValues = compoundPredictionValues;
	}

	public String getCurrentPageNumber() {
		return currentPageNumber;
	}
	public void setCurrentPageNumber(String currentPageNumber) {
		this.currentPageNumber = currentPageNumber;
	}

	public String getOrderBy() {
		return orderBy;
	}
	public void setOrderBy(String orderBy) {
		this.orderBy = orderBy;
	}

	public ArrayList<String> getPageNums() {
		return pageNums;
	}
	public void setPageNums(ArrayList<String> pageNums) {
		this.pageNums = pageNums;
	}
	
	public String getSortDirection() {
		return sortDirection;
	}
	public void setSortDirection(String sortDirection) {
		this.sortDirection = sortDirection;
	}
}