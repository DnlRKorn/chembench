package edu.unc.ceccr.action;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

//struts2
import com.mysql.jdbc.Util;
import com.opensymphony.xwork2.ActionSupport; 
import com.opensymphony.xwork2.ActionContext; 

import edu.unc.ceccr.global.Constants;
import edu.unc.ceccr.jobs.CentralDogma;
import edu.unc.ceccr.persistence.DataSet;
import edu.unc.ceccr.persistence.ExternalValidation;
import edu.unc.ceccr.persistence.HibernateUtil;
import edu.unc.ceccr.persistence.Job;
import edu.unc.ceccr.persistence.JobStats;
import edu.unc.ceccr.persistence.Prediction;
import edu.unc.ceccr.persistence.PredictionValue;
import edu.unc.ceccr.persistence.Predictor;
import edu.unc.ceccr.persistence.User;
import edu.unc.ceccr.utilities.ActiveUser;
import edu.unc.ceccr.utilities.FileAndDirOperations;
import edu.unc.ceccr.utilities.PopulateDataObjects;
import edu.unc.ceccr.utilities.Utility;

import org.apache.struts2.interceptor.ServletResponseAware;
import org.hibernate.Session;
import org.hibernate.Transaction;

import org.apache.log4j.Logger;

@SuppressWarnings("serial")

public class 
HomeAction extends ActionSupport implements ServletResponseAware 
{
    
    private static Logger logger = Logger.getLogger(HomeAction.class.getName());
    
    //loads home page
    
    protected HttpServletResponse servletResponse;
    @Override
    public void 
    setServletResponse(HttpServletResponse servletResponse)
    {
        this.servletResponse = servletResponse;
    }
    
    String visitors;
    String userStats;
    String jobStats;
    String cpuStats;
    String activeUsers;
    String runningJobs;
    String loginFailed = Constants.NO;
    User user;
    
    String username;
    String password;
    
    String showStatistics = Constants.YES; 
    
    public String 
    loadPage()
    {
        try {
            
            //stuff that needs to happen on server startup
            String debugText = "";
            if(Constants.doneReadingConfigFile)
            {
                debugText = "already read config file (?)";
            }
            else{
                try{
                    //STATIC PATH we didn't know how to make it dynamic in Struts 2

                    //storing the file outside the tomcat context, to not have to
                    //store it in git, else this file becomes visible to whole world with
                    // our public Cgit browser.
                    String path = Constants.SYSTEMCONFIG_XML_PATH;
                    
                    Utility.readBuildDateAndSystemConfig(path);
                }
                catch(Exception ex){
                    debugText += ex.getMessage();
                }
            }
            FileAndDirOperations.writeStringToFile(debugText, "/CHEMBENCH/prod/tomcat6/webapps/logs/debug-log.txt");
            
            //start up the queues, if they're not running yet
            CentralDogma.getInstance();
            
            //check if user is logged in
            ActionContext context = ActionContext.getContext();
            user = (User) context.getSession().get("user");

            //populate each string for the statistics section
            Session s = HibernateUtil.getSession();
            int numJobs = PopulateDataObjects.populateClass(Job.class, s).size();
            List<User> users = PopulateDataObjects.getUsers(s);
            List<JobStats> jobStatList = PopulateDataObjects.getJobStats(s);
            s.close();
            
            // cumulative visitors to the site
            int counter = 0;
            File counterFile = new File(Constants.CECCR_USER_BASE_PATH + "counter.txt");
            if (counterFile.exists()) {
                String counterStr = FileAndDirOperations.readFileIntoString(counterFile.getAbsolutePath()).trim();
                counter = Integer.parseInt(counterStr);
                FileAndDirOperations.writeStringToFile("" + (counter+1), counterFile.getAbsolutePath());
            }
            visitors = "Visitors: " + Integer.toString(counter);
            
            // number of registered users
            userStats = "Users: " + users.size();
    
            // finished jobs
            int numFinishedJobs = jobStatList.size();
            jobStats = "Jobs completed: "  + numFinishedJobs;
    
            // CPU statistics
            int computeHours = 0;
            String computeYearsStr = "";
            long timeDiffs = 0;
                
            for(JobStats js: jobStatList){
                if(js.getTimeFinished() != null && js.getTimeStarted() != null){
                    timeDiffs += js.getTimeFinished().getTime() - js.getTimeCreated().getTime();    
                }
            }
            int timeDiffInHours = Math.round(timeDiffs / 1000 / 60 / 60);
            computeHours = timeDiffInHours;
            float computeHoursf = computeHours;
            float computeYears = computeHoursf / new Float(24.0*365.0);
            computeYearsStr = Utility.floatToString(computeYears);
            Utility.roundSignificantFigures(computeYearsStr, 4);
            cpuStats = "Compute time used: "  + computeYearsStr + " years";
            
            // current users
            activeUsers = "Current Users: " + ActiveUser.getActiveSessions();
    
            // current number of jobs
            runningJobs = "Running Jobs: " + numJobs;

        }
        catch(Exception ex){
            Utility.writeToDebug(ex);
            showStatistics = "NO";
        }
        return SUCCESS;
    }
    
    public String execute() throws Exception {
        //log the user in
        String result = SUCCESS; 

        //check username and password
        ActionContext context = ActionContext.getContext();
        
        if(context.getParameters().get("username") != null){
            username = ((String[]) context.getParameters().get("username"))[0];
        }
        User user;
        
        if(context.getParameters().get("ip") != null){
            
            String ip =  ((String[]) context.getParameters().get("ip"))[0];
            long time = System.currentTimeMillis();
            String new_username = "guest"+ip+"_"+time;
            
            user = new User();

            user.setUserName(new_username);
            user.setEmail("ceccr@email.unc.edu");
            user.setFirstName("Guest");
            user.setLastName("Guest");
            user.setOrgName("Guest");
            user.setOrgType("Academia");
            user.setOrgPosition("Guest");
            user.setPhone("Guest");
            user.setAddress("Guest");
            user.setState("Guest");
            user.setCity("Guest");
            user.setCountry("Guest");
            user.setZipCode("Guest");
            
            //options
            user.setShowPublicDatasets(Constants.SOME);
            user.setShowPublicPredictors(Constants.ALL);
            user.setViewDatasetCompoundsPerPage(Constants.TWENTYFIVE);
            user.setViewPredictionCompoundsPerPage(Constants.TWENTYFIVE);
            user.setShowAdvancedKnnModeling(Constants.NO);
            
            //rights
            user.setCanDownloadDescriptors(Constants.NO);
            user.setIsAdmin(Constants.NO);
            
            //password 
            String password = "";
            user.setPassword(Utility.encrypt(password));
            
            user.setStatus("agree");
                
            Session s = HibernateUtil.getSession();
            Transaction tx = null;
        
            if(s==null){
                loginFailed = Constants.YES;
                return ERROR;
            }
            //commit user to DB
            
            try {
                tx = s.beginTransaction();
                s.saveOrUpdate(user);
                tx.commit();
            } catch (RuntimeException e) {
                if (tx != null)
                    tx.rollback();
                    loginFailed = Constants.YES;
                Utility.writeToDebug(e);
            } finally {
                s.close();
            }
            
            new Thread(new Runnable() {
                
                @Override
                public void run() {
                    
                    deleteOldGuests();
                    
                }
            }).start();
            
            context.getSession().put("user", user);
            context.getSession().put("userType", "guest");
            Cookie ckie = new Cookie("login","true");
            servletResponse.addCookie(ckie);
                
            Utility.writeToUsageLog("Logged in guest::", user.getUserName());
            
        }
        else{
        
            Session s = HibernateUtil.getSession();
            if (s==null)
            {
                logger.error("Found null exception at s.");
            }
            user = PopulateDataObjects.getUserByUserName(username, s);
            s.close();
        
        
            if(user != null){
                String realPasswordHash = user.getPassword();
                
                if (password != null && Utility.encrypt(password).equals(realPasswordHash)){
                    context.getSession().put("user", user);
                    Cookie ckie=new Cookie("login","true");
                    servletResponse.addCookie(ckie);
                    user.setLastLogintime(new Date());
                    
                    s = HibernateUtil.getSession();
                    Transaction tx = null;

                    try {
                        tx = s.beginTransaction();
                        s.saveOrUpdate(user);
                        tx.commit();
                    } catch (RuntimeException e) {
                        if (tx != null)
                            tx.rollback();
                        Utility.writeToDebug(e);
                    } finally {
                        s.close();
                    }
                    
                    
                    Utility.writeToUsageLog("Logged in", user.getUserName());
                }
                else{
                    loginFailed = Constants.YES;
                }
            }
            else{
                loginFailed = Constants.YES;
            }
        }
        loadPage();
        return result;
    }
    
    public String logout() throws Exception{
        ActionContext context = ActionContext.getContext();
        
        user = (User) context.getSession().get("user");
        
        if(user != null){
            Utility.writeToUsageLog("Logged out.", user.getUserName());
        }
        Utility.writeToDebug("************Logout action "+user.getUserName());
        
        if(user.getUserName().contains("guest") && context.getSession().get("userType")!=null && ((String)context.getSession().get("userType")).equals("guest")){
            deleteGuest(user);
        }
        context.getSession().remove("user"); 
        context.getSession().clear();
        
        Cookie ckie=new Cookie("login","false");
        servletResponse.addCookie(ckie);
        
        loadPage();
        return SUCCESS;
    }
    
    public boolean deleteGuest(User user){
        try{
            
            String userToDelete=user.getUserName();
            if(!userToDelete.trim().isEmpty()){
                Utility.writeToDebug("Delete GUEST");
                Session s = HibernateUtil.getSession();
                
                ArrayList<Prediction> predictions = (ArrayList<Prediction>) PopulateDataObjects.getUserData(userToDelete, Prediction.class, s);
                ArrayList<Predictor> predictors = (ArrayList<Predictor>) PopulateDataObjects.getUserData(userToDelete,Predictor.class, s);
                ArrayList<DataSet> datasets = (ArrayList<DataSet>) PopulateDataObjects.getUserData(userToDelete,DataSet.class, s);
                ArrayList<Job> jobs = (ArrayList<Job>) PopulateDataObjects.getUserData(userToDelete,Job.class, s);
                
                s.close();

                for(Prediction p: predictions){
                    Session session = HibernateUtil.getSession();    
                    ArrayList<PredictionValue> pvs = (ArrayList<PredictionValue>) PopulateDataObjects.getPredictionValuesByPredictionId(p.getId(), session);
                    
                    if(pvs != null){
                        for(PredictionValue pv : pvs){
                            Transaction tx = null;
                            try{
                                tx = session.beginTransaction();
                                session.delete(pv);
                                tx.commit();
                            }
                            catch (RuntimeException e) {
                                if (tx != null)
                                    tx.rollback();
                                Utility.writeToDebug(e);
                            }
                            finally{
                                session.close();
                            }
                        }
                    }
                    Transaction tx = null;
                    try{
                        session = HibernateUtil.getSession();
                        tx = session.beginTransaction();
                        session.delete(p);
                        tx.commit();
                    }catch (RuntimeException e) {
                        Utility.writeToDebug(e);
                    }
                    finally{
                        session.close();
                    }
                }

                for(Predictor p: predictors){
                    Session session = HibernateUtil.getSession();
                    ArrayList<ExternalValidation> extVals = new ArrayList<ExternalValidation>();
                    ArrayList<Predictor> childPredictors = new ArrayList<Predictor>();
                    if(p.getChildIds() != null && ! p.getChildIds().trim().equals("")){
                        String[] childIdArray = p.getChildIds().split("\\s+");
                        for(String childId: childIdArray){
                            Predictor childPredictor = PopulateDataObjects.getPredictorById(Long.parseLong(childId), session);
                            childPredictors.add(childPredictor);
                            extVals.addAll(PopulateDataObjects.getExternalValidationValues(childPredictor.getId(), session));
                        }
                    }
                    extVals.addAll(PopulateDataObjects.getExternalValidationValues(p.getId(), session));
                    session.close();
                    session = HibernateUtil.getSession();
                    Transaction tx = null;
                    try{
                        tx = session.beginTransaction();
                        session.delete(p);
                        session.close();
                        session = HibernateUtil.getSession();
                        tx = session.beginTransaction();
                        for(Predictor childPredictor: childPredictors){
                            session.delete(childPredictor);
                        }
                        for(ExternalValidation ev: extVals){
                            session.delete(ev);
                        }
                        tx.commit();
                    }catch (RuntimeException e) {
                        Utility.writeToDebug(e);
                    }
                    finally{
                        session.close();
                    }
                }

                for(DataSet d: datasets){
                    Session session = HibernateUtil.getSession();    
                    Transaction tx = null;
                    tx = session.beginTransaction();
                    session.delete(d);
                    tx.commit();
                }

                for(Job j: jobs){
                    CentralDogma.getInstance().localJobs.removeJob(j.getId());
                    Session session = HibernateUtil.getSession();    
                    Transaction tx = null;
                    tx = session.beginTransaction();
                    session.delete(j);
                    tx.commit();
                }

                try {
                    Session session = HibernateUtil.getSession();    
                    Transaction tx = null;
                    tx = session.beginTransaction();
                    session.delete(user);
                    tx.commit();
                    
                }
                catch(Exception ex){
                    Utility.writeToDebug(ex);
                }
                
                //last, delete all the files that user has
                Utility.writeToDebug("Delete GUEST:::ALL DATA FROM DB SHOULD BE DELETED");
                File dir= new File(Constants.CECCR_USER_BASE_PATH + userToDelete); 
                boolean flag = FileAndDirOperations.deleteDir(dir);//recurses
                Utility.writeToDebug("Delete GUEST:::ALL DATA FROM FILES SHOULD BE DELETED:"+flag);
            }
        }
        catch (Exception e) {
            Utility.writeToDebug(e);
            return false;
        }
        return true;
    }
    
    synchronized public void deleteOldGuests(){
        List<String> dirs= FileAndDirOperations.getGuestDirNames(new File(Constants.CECCR_USER_BASE_PATH));
        long currentTime = System.currentTimeMillis();
        for(String dir:dirs){
            long guestTime =  new Long(dir.substring(dir.lastIndexOf('_')+1)).longValue();
            if(!dir.trim().isEmpty() && currentTime-guestTime>Constants.GUEST_DATA_EXPIRATION_TIME){
                FileAndDirOperations.deleteDir(new File(Constants.CECCR_USER_BASE_PATH+dir));
                Utility.writeToDebug("DELETING OLD GUEST DATA:"+dir);
            }
        }
    }
    

    public String getVisitors() {
        return visitors;
    }

    public void setVisitors(String visitors) {
        this.visitors = visitors;
    }

    public String getUserStats() {
        return userStats;
    }

    public void setUserStats(String userStats) {
        this.userStats = userStats;
    }

    public String getJobStats() {
        return jobStats;
    }

    public void setJobStats(String jobStats) {
        this.jobStats = jobStats;
    }

    public String getCpuStats() {
        return cpuStats;
    }

    public void setCpuStats(String cpuStats) {
        this.cpuStats = cpuStats;
    }

    public String getActiveUsers() {
        return activeUsers;
    }

    public void setActiveUsers(String activeUsers) {
        this.activeUsers = activeUsers;
    }

    public String getRunningJobs() {
        return runningJobs;
    }

    public void setRunningJobs(String runningJobs) {
        this.runningJobs = runningJobs;
    }

    public String getShowStatistics() {
        return showStatistics;
    }

    public void setShowStatistics(String showStatistics) {
        this.showStatistics = showStatistics;
    }

    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getLoginFailed() {
        return loginFailed;
    }

    public void setLoginFailed(String loginFailed) {
        this.loginFailed = loginFailed;
    }
    
}
