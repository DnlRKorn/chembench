package edu.unc.ceccr.persistence;

import java.sql.DriverManager;
import java.sql.SQLException;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AnnotationConfiguration;

import edu.unc.ceccr.global.Constants;
import edu.unc.ceccr.persistence.Queue.QueueTask;
import edu.unc.ceccr.utilities.Utility;

public class HibernateUtil {
	
	private static final SessionFactory sessionFactory;
	private static String PASSWORD;
	private static String URL;
	private static String USERNAME;
	private static String DATABASENAME;
	private static int count = 0;
	
	static {
		sessionFactory = new AnnotationConfiguration()
		.addAnnotatedClass(QueueTask.class)
		.addAnnotatedClass(Model.class)
		.addAnnotatedClass(Predictor.class)
		.addAnnotatedClass(User.class)
		.addAnnotatedClass(Prediction.class)
		.addAnnotatedClass(PredictionValue.class)
		.addAnnotatedClass(ExternalValidation.class)
		.addAnnotatedClass(DataSet.class)
		.addAnnotatedClass(AdminSettings.class)
		.addAnnotatedClass(SoftwareExpiration.class)
		.addAnnotatedClass(Descriptors.class)
		.addAnnotatedClass(DescriptorGenerator.class)
		.addAnnotatedClass(PredictionTask.class)
		.addAnnotatedClass(ModelingTask.class)
		.addAnnotatedClass(VisualizationTask.class)
		.configure().buildSessionFactory();
	}

	public static Session getSession() throws HibernateException,
			ClassNotFoundException, SQLException {

		
		USERNAME=Constants.DATABASE_USERNAME;
		PASSWORD=Constants.CECCR_DATABASE_PASSWORD;
		URL=Constants.DATABASE_URL;
		DATABASENAME=Constants.CECCR_DATABASE_NAME;

		
		try{
			count++;
			//IMPORTANT: If you get a "too many connections" error
			//use this debug output to help trace where the wasteful connections are getting made!
			//Utility.writeToDebug("Making connection number: " + count);
			Class.forName(Constants.DATABASE_DRIVER);
			java.sql.Connection con = DriverManager.getConnection(URL + DATABASENAME, USERNAME, PASSWORD);
			Session s = sessionFactory.openSession(con);
			return s;
		}
		catch(Exception ex){
			Utility.writeToDebug(ex);
		}
		
		return null;
	}
}
