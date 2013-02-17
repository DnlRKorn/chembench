<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">

<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%@ taglib prefix="sx" uri="/struts-dojo-tags" %> 
<%@page language="java" import="java.util.*" %>

<html>
<head>
<title>CHEMBENCH | Reset Password </title>

<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />

<link href="theme/ccbStyle.css" rel="stylesheet" type="text/css">
<link href="theme/ccbStyleNavBar.css" rel="stylesheet" type="text/css">
<link rel="stylesheet" href="theme/screen.css" type="text/css" media="screen, projection">
<link rel="stylesheet" href="theme/print.css" type="text/css" media="print">
<link href="theme/standard.css" rel="stylesheet" type="text/css">
<link href="theme/links.css" rel="stylesheet" type="text/css">
<link href="theme/dynamicTab.css" rel="stylesheet" type="text/css">
<link rel="icon" href="/theme/img/mml.ico" type="image/ico">
<link rel="SHORTCUT ICON" href="/theme/img/mml.ico">
<link href="theme/customStylesheet.css" rel="stylesheet" type="text/css">

<script src="javascript/script.js"></script>

</head>
<body>
<!-- Navigation bar -->
<div class="outer">

    <div class="includesHeader"><%@include file="/jsp/main/header.jsp" %></div>
    <div class="includesNavbar"><%@include file="/jsp/main/centralNavigationBar.jsp" %></div>

<!-- Main page -->
<table width="924" border="0" align="center" cellpadding="0" cellspacing="0">
	<tr>
		<span id="maincontent">
		<td height="557" colspan="2" valign="top" background="theme/img/backgrindex.jpg">
			<table>
				<tr>
					<td class="ChangePSText">
						<form action="resetPassword" ><br/>
							<b>Reset Your Password</b><br/><br/>
	          				Your username: <br/>
	          				<s:textfield name="userName" size="20" theme="simple" />
			 				<br/><br/>
	           				Your email address: <br/>
	         				<s:textfield name="email" size="35" theme="simple" />
			    			<br /><br />
			    			<input type="submit" value="Submit" >
			    			<br /><br />
			    			<s:property value="errorMessage" />
		    			</form>
     				</td>
     			</tr>
     		</table>
		</td>
		</span>
	</tr>
    <div class="includes"><%@include file ="/jsp/main/footer.jsp" %></div>

</div>
</body>
</html>
