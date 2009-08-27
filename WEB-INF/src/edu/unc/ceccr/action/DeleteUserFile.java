package edu.unc.ceccr.action;

import java.sql.SQLException;
import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Expression;

import edu.unc.ceccr.global.Constants;
import edu.unc.ceccr.persistence.DataSet;
import edu.unc.ceccr.persistence.HibernateUtil;
import edu.unc.ceccr.persistence.Prediction;
import edu.unc.ceccr.persistence.Queue;
import edu.unc.ceccr.persistence.Queue.QueueTask;
import edu.unc.ceccr.persistence.Queue.QueueTask.jobTypes;
import edu.unc.ceccr.taskObjects.QsarModelingTask;
import edu.unc.ceccr.taskObjects.QsarPredictionTask;
import edu.unc.ceccr.utilities.FileAndDirOperations;
import edu.unc.ceccr.utilities.PopulateDataObjects;
import edu.unc.ceccr.utilities.Utility;

public class DeleteUserFile extends Action {

	ActionMapping mapping;

	private ActionForward forward;

	public ActionForward execute(ActionMapping mapping, ActionForm form,	HttpServletRequest request, HttpServletResponse response) {

		forward = mapping.findForward("success");
		
		HttpSession session = request.getSession(false);
		
		if (session == null) {
			forward = mapping.findForward("login");
		}else if (session.getAttribute("user") == null){
			forward = mapping.findForward("login");
		}else{
			try {
				String userName=request.getParameter("userName");
				
				String fileName=request.getParameter("fileName");
				
				String msg = "Cannot delete dataset! ";
				
				String jobname = checkJobNames(userName, fileName);
				/*String predictorname = checkPredictions(userName, fileName);
				String modellingname = checkModelling(userName, fileName);
				*/
				String mp = checkTasks(userName, fileName);
				Utility.writeToMSDebug("DELETE DATASET JOBNAMES CHECK:"+jobname);
				Utility.writeToMSDebug("DELETE DATASET PREDICTORNAMES CHECK:"+mp);
				//Utility.writeToMSDebug("DELETE DATASET ModellingNAMES CHECK:"+modellingname);
				
				if(jobname==null && mp==null) 
					deleteDataset(userName, fileName);
				else{
					if(jobname!=null) msg += "Job "+jobname+" is using it! ";
					/*if(predictorname!=null) msg += "Prediction job "+predictorname+" is using it! ";
					if(modellingname!=null) msg += "Modelling job "+modellingname+" is using it! ";*/
					if(mp!=null) msg+=mp;
					request.removeAttribute("validationMsg");
					request.setAttribute("validationMsg", msg);
					forward = mapping.findForward("failure");
				}
			} catch (Exception e) {
				forward = mapping.findForward("failure");
				Utility.writeToDebug(e);
			}
		}
		return (forward);

	}
	
	
	
	public void deleteDataset(String userName, String fileName)throws ClassNotFoundException, SQLException
	{
		DataSet dataSet=new DataSet();
				
		Session session = HibernateUtil.getSession();
		
		Transaction tx=null,tx2=null;
		
		try{
			tx=session.beginTransaction();
			dataSet=(DataSet)session.createCriteria(DataSet.class).add(Expression.eq("userName", userName)).add(Expression.eq("fileName",fileName))
			               .uniqueResult();
			tx.commit();
		}catch (RuntimeException e) {
			if (tx != null)
				tx.rollback();
			Utility.writeToDebug(e);
		}
		
		String dir = Constants.CECCR_USER_BASE_PATH+userName+"/DATASETS/"+fileName;
		Utility.writeToMSDebug("DEL::"+dir);
				
		//deleting dataset from job
		
		List<QueueTask> tasks = (List<QueueTask>) Queue.getInstance().getUserTasks(userName);
				for(Iterator<QueueTask> i=tasks.iterator();i.hasNext();){
			QueueTask temp = i.next();
			if(temp.jobType.equals(jobTypes.dataset) && temp.getJobName().equals(fileName)){
				Queue.getInstance().deleteTask(temp);
			}
			if(temp.jobType.equals(jobTypes.dataset) && temp.getJobName().equals(fileName+"_sketches_generation")){
				Queue.getInstance().deleteTask(temp);
			}
		}
				
		tasks = (List<QueueTask>) Queue.getInstance().getQueuedTasks();
		for(Iterator<QueueTask> i=tasks.iterator();i.hasNext();){
			QueueTask temp = i.next();
			if(temp.getUserName().equals(userName) && temp.getJobName().equals(fileName) || temp.getJobName().equals(fileName+"_sketches_generation")){
				Queue.getInstance().deleteTask(temp);
			}
		}
			
		//
		Utility.writeToMSDebug("DeleteUserFile::"+dir);
		if(FileAndDirOperations.deleteDir(new File(dir)))
		{
			try{
				tx2=session.beginTransaction();
			    session.delete(dataSet);
			  // session.delete(predDatabase);
			    tx2.commit();
			}catch (RuntimeException e) {
				if (tx2 != null)
					tx2.rollback();
				Utility.writeToDebug(e);
			}
		}
		session.close();
		
	}
	

	
	
	
}
