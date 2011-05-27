package edu.unc.ceccr.action;

import java.util.ArrayList;
import java.util.Date;

//struts2
import com.opensymphony.xwork2.ActionSupport; 
import com.opensymphony.xwork2.ActionContext; 

import net.tanesha.recaptcha.ReCaptcha;
import net.tanesha.recaptcha.ReCaptchaFactory;
import net.tanesha.recaptcha.ReCaptchaResponse;

import org.apache.tools.ant.taskdefs.SendEmail;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Expression;

import edu.unc.ceccr.global.Constants;
import edu.unc.ceccr.persistence.HibernateUtil;
import edu.unc.ceccr.persistence.User;
import edu.unc.ceccr.utilities.PopulateDataObjects;
import edu.unc.ceccr.utilities.SendEmails;
import edu.unc.ceccr.utilities.Utility;

public class ResetPasswordAction extends ActionSupport {
	String userName;
	String email;
	String successMessage;
	String errorMessage;
	
	public String execute() throws Exception {
		
		// set up session to check user name and email
		Session s = HibernateUtil.getSession();
		User user = PopulateDataObjects.getUserByUserName(userName, s);
		if (user == null || !user.getEmail().equals(email)){
			errorMessage = "Invalid username or email!";
			 return ERROR;	
		}

		//email matches
		String randomPassword = Utility.randomPassword();
		user.setPassword(randomPassword);

		// Commit changes
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
		
		// message to user
		
		//email
		String message=user.getFirstName()+", your Chembench password has been reset."+"<br/>"+"Your username: "+user.getUserName()
		+"<br/> Your new password is: "+randomPassword+"<br/><br/><br/>"
		+"You may login from "+Constants.WEBADDRESS+".<br/> <br/><br/>"
		+"Once you are logged in, you may change your password from the 'edit profile' page.";
		
		SendEmails.sendEmail(email, "", "", "Chembench Password Reset", message);

		// web page
		successMessage = "Your password has been reset. " +
		"An email containing the password has been sent to " + user.getEmail()
		+". When the email arrives, you'll want to return to Home page and log in. "
		+"You may change your password from the 'edit profile' page when you are logged in.";
		
		return SUCCESS;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getSuccessMessage() {
		return successMessage;
	}

	public void setSuccessMessage(String successMessage) {
		this.successMessage = successMessage;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

}