package edu.unc.ceccr.action.RegisterActions;

import java.util.List;
import java.util.Iterator;
import java.io.PrintWriter;
import java.util.ArrayList;

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

import edu.unc.ceccr.formbean.EmailToAllBean;
import edu.unc.ceccr.persistence.HibernateUtil;
import edu.unc.ceccr.persistence.User;
import edu.unc.ceccr.utilities.SendEmails;
import edu.unc.ceccr.utilities.Utility;

public class EmailAllAction extends Action {

	@SuppressWarnings("unchecked")
	public ActionForward execute(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response)throws Exception {

		ActionForward forward = new ActionForward(); 
		HttpSession session = request.getSession(false);
		
		if (session == null) {forward = mapping.findForward("login");
		} 
		else if (session.getAttribute("user") == null) { forward = mapping.findForward("login");
		} 
		else {
            EmailToAllBean  formBean=(EmailToAllBean)form;
			List<User> userList=new ArrayList<User>();
			
			try {
				Session s = HibernateUtil.getSession();
				Transaction tx = null;
				try {
					tx = s.beginTransaction();
					userList=s.createCriteria(User.class).add(Expression.eq("status","agree")).list();
					tx.commit();
				} catch (RuntimeException e) {
					if (tx != null)
						tx.rollback();
					Utility.writeToDebug(e);				forward = mapping.findForward("failure");
				} finally {	s.close(); }
				Iterator it=userList.iterator();
				while(it.hasNext())
				{
					SendEmails.sendEmail((User)it.next(),formBean);
				}
				PrintWriter pw=response.getWriter();
				pw.write("<script> window.alert('The emails have been sent to all users!');window.close();</script>");
				forward = mapping.findForward("success");
			} catch (Exception e) {
				forward = mapping.findForward("failure");
				Utility.writeToDebug(e);
			}
		}
		
		return forward;
	}
		
}
