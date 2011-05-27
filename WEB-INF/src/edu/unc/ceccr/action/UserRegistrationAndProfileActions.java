package edu.unc.ceccr.action;

import java.util.ArrayList;
import java.util.Date;

//struts2
import com.opensymphony.xwork2.ActionSupport; 
import com.opensymphony.xwork2.ActionContext; 

import net.tanesha.recaptcha.ReCaptcha;
import net.tanesha.recaptcha.ReCaptchaFactory;
import net.tanesha.recaptcha.ReCaptchaResponse;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Expression;

import edu.unc.ceccr.global.Constants;
import edu.unc.ceccr.persistence.HibernateUtil;
import edu.unc.ceccr.persistence.User;
import edu.unc.ceccr.utilities.SendEmails;
import edu.unc.ceccr.utilities.Utility;

public class UserRegistrationAndProfileActions extends ActionSupport {

	/* USER FUNCTIONS */
	
	public String loadUserRegistration() throws Exception{
		String result = SUCCESS;
		organizationType = "Academia";
		return result;
	}
	
	public String loadEditProfilePage() throws Exception{
		String result = SUCCESS;
		//check that the user is logged in
		ActionContext context = ActionContext.getContext();
		user = getLoggedInUser(context);
		if(user == null){
			return LOGIN;
		}
		if(Utility.isAdmin(user.getUserName())){
			userIsAdmin = true;
		}
		if(user.getUserName().equals("guest")){
			 errorStrings.add("Error: You may not change the guest profile settings.");
			return ERROR;
		}
		return result;
	}
	
	public String registerUser() throws Exception{
		ActionContext context = ActionContext.getContext();
		String result = SUCCESS;
		//form validation
			//Validate that each required field has something in it.
			validateUserInfo(); //this function will populate the errorMessages arraylist.
			
			if(! errorMessages.isEmpty()){
				result = ERROR;
			}
			
			if(newUserName.isEmpty()){
		    	errorMessages.add("Please enter a user name.");
		    	result = ERROR;
			}	
			
			//Check whether the username already exists 
			//(queries database)
			if(!newUserName.equals("") && userExists(newUserName)){
		    	errorMessages.add("The user name '"+newUserName+"' is already in use.");
				result = ERROR;
			}
			else if(newUserName.contains(" ")){
				errorMessages.add("Your username may not contain a space.");
				result = ERROR;
			}
			
			//check CAPTCHA
			ReCaptcha captcha = ReCaptchaFactory.newReCaptcha(Constants.RECAPTCHA_PUBLICKEY,Constants.RECAPTCHA_PRIVATEKEY, false);

			ReCaptchaResponse resp = captcha.checkAnswer("127.0.0.1", 
	        		((String[])context.getParameters().get("recaptcha_challenge_field"))[0], 
	        		((String[])context.getParameters().get("recaptcha_response_field"))[0]);
	        
		    if (!resp.isValid()) {
		    	errorMessages.add("The text you typed for the CAPTCHA test did not match the picture. Try again.");
	        	result = ERROR;
	        }
			
			if(result.equals(ERROR)){
				return result;
			}
				
			//make user
			user = new User();

			user.setUserName(newUserName);
			user.setEmail(email);
			user.setFirstName(firstName);
			user.setLastName(lastName);
			user.setOrgName(organizationName);
			user.setOrgType(organizationType);
			user.setOrgPosition(organizationPosition);
			user.setPhone(phoneNumber);
			user.setAddress(address);
			user.setState(stateOrProvince);
			user.setCity(city);
			user.setCountry(country);
			user.setZipCode(zipCode);
			user.setWorkbench(workBench); //deprecated, but may come back
			
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
			String password = Utility.randomPassword();
			user.setPassword(Utility.encrypt(password));
			
			user.setStatus("agree");
				
			Session s = HibernateUtil.getSession();
			Transaction tx = null;
		
		//commit user to DB
			
			try {
				tx = s.beginTransaction();
				s.saveOrUpdate(user);
				tx.commit();
			} catch (RuntimeException e) {
				if (tx != null)
					tx.rollback();
				Utility.writeToDebug(e);
			} finally {s.close();}
			
			//user is auto-approved; email them a temp password

			outputMessage = "Your account has been created! " +
			"An email containing your password has been sent to " + email + 
			". Please check your email and log in to Chembench. " +
			"Note: Email delivery may be delayed up to 15 minutes depending on email server load.";
			
			String HtmlBody = "Thank you for you interest in CECCR's Chembench. <br/>Your account has been approved.<br/>"
	  			+"<br/> Your user name : <font color=red>"+ user.getUserName() + "</font>"
	  			+"<br/> Your temporary password : <font color=red>" + password + "</font>" 
	  			+"<br/> Please note that passwords are case sensitive. "
	  			+"<br/> In order to change your password,  log in to Chembench at <a href='http://chembench.mml.unc.edu'>http://chembench.mml.unc.edu</a> and click the 'edit profile' link at the upper right."
	  			+"<br/>"
	  			+"<br/>We hope that you find Chembench to be a useful tool. <br/>If you have any problems or suggestions for improvements, please contact us at : "+Constants.WEBSITEEMAIL
	  			+"<br/><br/>Thank you. <br/>The Chembench Team<br/>";
	
  			SendEmails.sendEmail(user.getEmail(), "", "", "Chembench User Registration", HtmlBody);

  			Utility.writeToUsageLog("just registered!", newUserName);
	  		Utility.writeToDebug("In case email failed, temp password for user: " + user.getUserName() + " is: " + password);

			user = null; //if user != null, it will show a "You are logged in" message. That's bad.
		return result;
	}
	
	public String loadChangePassword() throws Exception{
		String result = SUCCESS;
		return result;
	}
	
	public String changePassword() throws Exception{
		String result = SUCCESS;
		
		//check that the user is logged in
		ActionContext context = ActionContext.getContext();
		user = getLoggedInUser(context);
		if(user == null){
			return LOGIN;
		}

		if(Utility.isAdmin(user.getUserName())){
			userIsAdmin = true;
		}
		
		String realPasswordHash=user.getPassword();
		if (! (Utility.encrypt(oldPassword).equals(realPasswordHash))){
			errorMessages.add("You entered your old password incorrectly. Your password was not changed. Please try again.");
		}
		
		if(!errorMessages.isEmpty()){
			errorMessages.add(0, "Error changing password.");
			return ERROR;
		}
			
		// Change user object to have new password
		Utility.writeToDebug("Changing user password");
		user.setPassword(Utility.encrypt(newPassword));
		
		// Commit changes
		
		Session s = HibernateUtil.getSession();
		Transaction tx = null;
	
		try {
			tx = s.beginTransaction();
			s.saveOrUpdate(user);
			tx.commit();
		} catch (RuntimeException e) {
			if (tx != null)
				tx.rollback();
			Utility.writeToDebug(e);
		} finally {s.close();}
		
		errorMessages.add("Password change successful!");

		
		return result;
	}

	public String loadUpdateUserInformation() throws Exception{
		String result = SUCCESS;
		ActionContext context = ActionContext.getContext();
		user = getLoggedInUser(context);
		if(user == null){
			return LOGIN;
		}
		
		address = user.getAddress();
		city = user.getCity();
		country = user.getCountry();
		email = user.getEmail();
		firstName = user.getFirstName();
		lastName = user.getLastName();
		organizationName = user.getOrgName();
		organizationType = user.getOrgType();
		organizationPosition = user.getOrgType();
		phoneNumber = user.getPhone();
		stateOrProvince = user.getState();
		zipCode = user.getZipCode();
		workBench = user.getWorkbench();
		
		return result;
	}
	
	public String updateUserInformation() throws Exception{
		String result = SUCCESS;
		
		//check that the user is logged in
		ActionContext context = ActionContext.getContext();
		user = getLoggedInUser(context);
		if(user == null){
			return LOGIN;
		}
		
		//validate each field
		validateUserInfo();
		if(! errorMessages.isEmpty()){
			return ERROR;
		}
		
		// Change user object according to edited fields
		Utility.writeToDebug("Updating user information");
		user.setEmail(email);
		user.setFirstName(firstName);
		user.setLastName(lastName);
		user.setOrgName(organizationName);
		user.setOrgType(organizationType);
		user.setOrgPosition(organizationPosition);
		user.setPhone(phoneNumber);
		user.setAddress(address);
		user.setState(stateOrProvince);
		user.setCity(city);
		user.setCountry(country);
		user.setZipCode(zipCode);
		user.setWorkbench(workBench); //deprecated, but some people think it's still important
		
		// Commit changes
		Session s = HibernateUtil.getSession();
		Transaction tx = null;
		try {
			tx = s.beginTransaction();
			s.saveOrUpdate(user);
			tx.commit();
		} catch (RuntimeException e) {
			if (tx != null)
				tx.rollback();
			Utility.writeToDebug(e);
		} finally {s.close();}

		errorMessages.add("Your information has been updated!");
		
		return result;
	}

	public String loadUpdateUserOptions() throws Exception{
		String result = SUCCESS;
		//check that the user is logged in
		ActionContext context = ActionContext.getContext();
		user = getLoggedInUser(context);
		if(user == null){
			return LOGIN;
		}
		showPublicDatasets = user.getShowPublicDatasets();
		showPublicPredictors = user.getShowPublicPredictors();
		viewDatasetCompoundsPerPage = user.getViewDatasetCompoundsPerPage();
		viewPredictionCompoundsPerPage = user.getViewPredictionCompoundsPerPage();
		showAdvancedKnnModeling = user.getShowAdvancedKnnModeling();
		
		return result;
	}
	
	public String updateUserOptions() throws Exception{
		String result = SUCCESS;
		
		//check that the user is logged in
		ActionContext context = ActionContext.getContext();
		user = getLoggedInUser(context);
		if(user == null){
			return LOGIN;
		}
		
		// Change user object according to edited fields
		Utility.writeToDebug("Changing user options");
		user.setShowPublicDatasets(showPublicDatasets);
		user.setShowPublicPredictors(showPublicPredictors);
		user.setViewDatasetCompoundsPerPage(viewDatasetCompoundsPerPage);
		user.setViewPredictionCompoundsPerPage(viewPredictionCompoundsPerPage);
		user.setShowAdvancedKnnModeling(showAdvancedKnnModeling);
		
		// Commit changes
		Session s = HibernateUtil.getSession();
		Transaction tx = null;
		try {
			tx = s.beginTransaction();
			s.saveOrUpdate(user);
			tx.commit();
		} catch (RuntimeException e) {
			if (tx != null)
				tx.rollback();
			Utility.writeToDebug(e);
		} finally {s.close();}

		errorMessages.add("Your settings have been saved!");
		
		return result;
	}
	
	/* USER FUNCTIONS */
	
	/* ADMIN-ONLY FUNCTIONS */
	//These do not belong here. They should be moved when 
	//the Admin page has been struts2ified.
	/*
	public String ChangeModelingLimits() throws Exception{
		String result = SUCCESS;
		
		//check that the user is logged in and is an admin
		ActionContext context = ActionContext.getContext();
		user = getLoggedInUser(context);
		if(user == null){
			return LOGIN;
		}
		if(! Utility.isAdmin(user.getUserName())){
			return LOGIN;
		}
		
		
		
		return result;
	}
	
	public String UpdateSoftwareExpiration() throws Exception{
		String result = SUCCESS;
		
		//check that the user is logged in and is an admin
		ActionContext context = ActionContext.getContext();
		user = getLoggedInUser(context);
		if(user == null){
			return LOGIN;
		}
		if(! Utility.isAdmin(user.getUserName())){
			return LOGIN;
		}
		
		
		
		return result;
	}
	
	public String DenyJob() throws Exception{
		String result = SUCCESS;
		
		//check that the user is logged in and is an admin
		ActionContext context = ActionContext.getContext();
		user = getLoggedInUser(context);
		if(user == null){
			return LOGIN;
		}
		if(! Utility.isAdmin(user.getUserName())){
			return LOGIN;
		}
		
		
		
		return result;
	}
	
	public String PermitJob() throws Exception{
		String result = SUCCESS;
		
		//check that the user is logged in and is an admin
		ActionContext context = ActionContext.getContext();
		user = getLoggedInUser(context);
		if(user == null){
			return LOGIN;
		}
		if(! Utility.isAdmin(user.getUserName())){
			return LOGIN;
		}
		
		return result;
	}
	*/
	/* END ADMIN-ONLY FUNCTIONS */
	
	/* HELPER FUNCTIONS */
	
	private User getLoggedInUser(ActionContext context){
		if(context == null){
			Utility.writeToStrutsDebug("No ActionContext available");
			return null;
		}
		else{
			user = (User) context.getSession().get("user");
			return user;
		}
	}
	
	private boolean userExists(String userName)throws Exception
	{
		Session s = HibernateUtil.getSession();// query
		Transaction tx = null;
		User user=null;
		User userInfo=null;
		try {
			tx = s.beginTransaction();
			user=(User)s.createCriteria(User.class).add(Expression.eq("userName",userName))
			      .uniqueResult();
			userInfo=(User)s.createCriteria(User.class)
			       .add(Expression.eq("userName", userName)).uniqueResult();
			tx.commit();
		} catch (RuntimeException e) {
			if (tx != null)
				tx.rollback();
			Utility.writeToDebug(e);
		} finally {
			s.close();
		}
		if(user==null&&userInfo==null)
		{
			return false;
		}else{return true;}
	}
	
	public void validateUserInfo(){
		if(firstName.isEmpty()){
	    	errorMessages.add("Please enter your first name.");
		}
		if(lastName.isEmpty()){
	    	errorMessages.add("Please enter your last name.");
		}
		if(organizationName.isEmpty()){
	    	errorMessages.add("Please enter your organization name.");
		}
		if(organizationPosition.isEmpty()){
	    	errorMessages.add("Please enter your organization position.");
		}
		if(email.isEmpty() || ! email.contains("@") || ! email.contains(".")){
	    	errorMessages.add("Please enter a valid email address.");
		}
		if(city.isEmpty()){
	    	errorMessages.add("Please enter your city.");
		}
		if(country.isEmpty()){
	    	errorMessages.add("Please enter your country.");
		}
	}
	
	/* END HELPER FUNCTIONS */
		
	
	/* DATA OBJECTS, GETTERS, AND SETTERS */
	
	private User user;

	/* Variables used for user registration and updates */
	private String recaptchaPublicKey = Constants.RECAPTCHA_PUBLICKEY;
	private ArrayList<String> errorMessages = new ArrayList<String>();
	private ArrayList<String> errorStrings = new ArrayList<String>();
	private String outputMessage;
	
	private String newUserName;
	private String address;
	private String city;
	private String country;
	private String email;
	private String firstName;
	private String lastName;
	private String organizationName;
	private String organizationType;
	private String organizationPosition;
	private String phoneNumber;
	private String stateOrProvince;
	private String zipCode;
	private String workBench; //deprecated, but some people think it's still important
	/* End Variables used for user registration and updates */
	
	/* Variables used in password changes and user options */
	private String oldPassword;
	private String newPassword;
	private String showPublicDatasets;
	private String showPublicPredictors;
	private String viewDatasetCompoundsPerPage;
	private String viewPredictorModels;
	private String viewPredictionCompoundsPerPage;
	private String showAdvancedKnnModeling;

	private boolean userIsAdmin = false;
	
	/* End Variables used in password changes and user options */
	
	public User getUser() {
		return user;
	}
	public void setUser(User user) {
		this.user = user;
	}
	
	/* Variables used for user registration and updates */
	public String getRecaptchaPublicKey() {
		return recaptchaPublicKey;
	}
	public void setRecaptchaPublicKey(String recaptchaPublicKey) {
		this.recaptchaPublicKey = recaptchaPublicKey;
	}	
	
	public ArrayList<String> getErrorMessages() {
		return errorMessages;
	}
	public void setErrorMessages(ArrayList<String> errorMessages) {
		this.errorMessages = errorMessages;
	}
	
	public ArrayList<String> getErrorStrings() {
		return errorStrings;
	}
	public void setErrorStrings(ArrayList<String> errorStrings) {
		this.errorStrings = errorStrings;
	}

	public String getOutputMessage() {
		return outputMessage;
	}
	public void setOutputMessage(String outputMessage) {
		this.outputMessage = outputMessage;
	}

	public String getNewUserName() {
		return newUserName;
	}
	public void setNewUserName(String newUserName) {
		this.newUserName = newUserName;
	}

	public String getAddress() {
		return address;
	}
	public void setAddress(String address) {
		this.address = address;
	}

	public String getCity() {
		return city;
	}
	public void setCity(String city) {
		this.city = city;
	}

	public String getCountry() {
		return country;
	}
	public void setCountry(String country) {
		this.country = country;
	}

	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}

	public String getFirstName() {
		return firstName;
	}
	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}
	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getOrganizationName() {
		return organizationName;
	}
	public void setOrganizationName(String organizationName) {
		this.organizationName = organizationName;
	}

	public String getOrganizationType() {
		return organizationType;
	}
	public void setOrganizationType(String organizationType) {
		this.organizationType = organizationType;
	}

	public String getOrganizationPosition() {
		return organizationPosition;
	}
	public void setOrganizationPosition(String organizationPosition) {
		this.organizationPosition = organizationPosition;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}
	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public String getStateOrProvince() {
		return stateOrProvince;
	}
	public void setStateOrProvince(String stateOrProvince) {
		this.stateOrProvince = stateOrProvince;
	}

	public String getZipCode() {
		return zipCode;
	}
	public void setZipCode(String zipCode) {
		this.zipCode = zipCode;
	}

	public String getWorkBench() {
		return workBench;
	}
	public void setWorkBench(String workBench) {
		this.workBench = workBench;
	}
	/* End Variables used for user registration and updates */
	
	
	/* Variables used in password changes and user options */
	public String getOldPassword() {
		return oldPassword;
	}
	public void setOldPassword(String oldPassword) {
		this.oldPassword = oldPassword;
	}

	public String getNewPassword() {
		return newPassword;
	}
	public void setNewPassword(String newPassword) {
		this.newPassword = newPassword;
	}

	public String getShowPublicDatasets() {
		return showPublicDatasets;
	}
	public void setShowPublicDatasets(String showPublicDatasets) {
		this.showPublicDatasets = showPublicDatasets;
	}

	public String getShowPublicPredictors() {
		return showPublicPredictors;
	}
	public void setShowPublicPredictors(String showPublicPredictors) {
		this.showPublicPredictors = showPublicPredictors;
	}
	
	public String getViewDatasetCompoundsPerPage() {
		return viewDatasetCompoundsPerPage;
	}
	public void setViewDatasetCompoundsPerPage(String viewDatasetCompoundsPerPage) {
		this.viewDatasetCompoundsPerPage = viewDatasetCompoundsPerPage;
	}

	public String getViewPredictorModels() {
		return viewPredictorModels;
	}
	public void setViewPredictorModels(String viewPredictorModels) {
		this.viewPredictorModels = viewPredictorModels;
	}

	public String getViewPredictionCompoundsPerPage() {
		return viewPredictionCompoundsPerPage;
	}
	public void setViewPredictionCompoundsPerPage(String viewPredictionCompoundsPerPage) {
		this.viewPredictionCompoundsPerPage = viewPredictionCompoundsPerPage;
	}

	public String getShowAdvancedKnnModeling() {
		return showAdvancedKnnModeling;
	}
	public void setShowAdvancedKnnModeling(String showAdvancedKnnModeling) {
		this.showAdvancedKnnModeling = showAdvancedKnnModeling;
	}
	
	public boolean isUserIsAdmin() {
		return userIsAdmin;
	}
	public void setUserIsAdmin(boolean userIsAdmin) {
		this.userIsAdmin = userIsAdmin;
	}
	/* End Variables used in password changes and user options */
	
	/* END DATA OBJECTS, GETTERS, AND SETTERS */
}	