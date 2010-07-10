package edu.unc.ceccr.persistence;

import javax.persistence.*;

import edu.unc.ceccr.utilities.Utility;

@Entity
@Table(name = "cbench_knnPlusModel")
public class KnnPlusModel implements java.io.Serializable{

	//check that model progress works
	//make a database table that holds this info
	
	private String ModelId;
	private String Datafile;								
	private String Ndims;
	private String DimsIDs;				
	private String DimsNames;								
	private String kOrR;	
	private String QualityLimit;	
	private String Ndatapoints;		
	private String stdevAct;	
	private String stdevActCalc;	
	private String b01;			
	private String b11;			
	private String b02;			
	private String b12;			
	private String R;			
	private String R2;			
	private String MSE1;		
	private String MSE2;	
	private String F1;		
	private String F2;		
	private String k1;			
	private String k2;			
	private String R02;		
	private String R012;		
	private String MSE01;		
	private String MSE02;		
	private String F01;		
	private String F02;		
	private String q2;			
	private String qPrime2;	
	private String MAEq;		
	private String MAEqPrime;		
	private String MSE;			
	private String MAE;				
	
	/*

this is the models.tbl file generated by knn+.
It's pretty much what we're gonna want to display.
Need to check that it gives the same outputs for all settings (error based fit?) etc.

	ModelID				Datafile								Ndims	DimsIDs				DimsNames								k(R)	QualityLimit	Ndatapoints		st.dev(Act.)	st.dev(Act.calc.)	b01			b11			b02			b12			R			R^2			MSE1		MSE2		F1		F2		k1			k2			R0^2		R01^2		MSE01		MSE02		F01		F02		q^2			q'^2		MAEq		MAEq'		MSE			MAE				QualityLimit	Ndatapoints		st.dev(Act.)	st.dev(Act.calc.)	b01			b11			b02			b12			R			R^2			MSE1		MSE2		F1		F2		k1			k2		R0^2		R01^2		MSE01		MSE02		F01		F02		q^2			q'^2		MAEq		MAEq'		MSE			MAE	
1	rand_sets_0_trn0.x rand_sets_0_trn0.a rand_sets.list split#1	5	13 70 160 256 284	nXp4 dXp10 n2Pag33 minSHssNH minSdO		5		0.746673		87				1.19074			1.03929				-0.026311	0.990986	0.522417	0.75494		0.864948	0.748135	0.353003	0.26892		252.483	252.483	0.980588	0.950646	0.748031	0.679348	0.353149	0.342366	247.117	319.375	0.746673	0.667466	0.483377	0.385889	0.355052	0.464056		0.701676		22				0.824228		0.951481			0.413612	0.725631	0.161796	0.966988	0.837661	0.701676	0.193455	0.257801	47.0413	47.0413	0.902241	1.04244	0.649981	0.69653		0.226978	0.262248	62.564	53.7994	0.584988	0.688574	0.397374	0.428144	0.269124	0.394994
2	rand_sets_0_trn0.x rand_sets_0_trn0.a rand_sets.list split#1	5	13 70 160 241 284	nXp4 dXp10 n2Pag33 SssNH minSdO			5		0.746633		87				1.19074			1.0393				-0.0262493	0.990953	0.522451	0.754925	0.864925	0.748095	0.353059	0.268966	252.429	252.429	0.980579	0.950644	0.747991	0.6793		0.353204	0.342422	247.077	319.322	0.746633	0.667417	0.483331	0.38584		0.355109	0.464097		0.70161			22				0.824228		0.951534			0.413683	0.725556	0.161878	0.966995	0.837621	0.70161		0.193498	0.257887	47.0263	47.0263	0.902187	1.04249	0.649896	0.696458	0.227033	0.262339	62.5485	53.7856	0.584826	0.688486	0.397229	0.428092	0.269229	0.395089

	 */
	
	
}