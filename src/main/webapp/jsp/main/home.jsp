<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<!DOCTYPE html>
<html>
<head>
  <%@ include file="/jsp/main/head.jsp" %>
  <title>Chembench | Home</title>
</head>
<body>
<div id="main" class="container">
  <%@ include file="/jsp/main/header.jsp" %>

  <div id="content">
    <div class="row">
      <section class="col-xs-8">
        <h2>Accelerating chemical genomics research</h2>
        <hr>
        <div>
          <p>Chembench is a free portal that enables researchers to mine available chemical and biological data.
            Chembench can help researchers rationally design or select new compounds or compound libraries with
            significantly enhanced hit rates in screening experiments.
          </p>
          <img id="home-interstitial-banner" class="interstitial"
               src="${pageContext.request.contextPath}/assets/images/home-interstitial-banner.jpg"
               alt="Molecule image banner">
          <p>
            Chembench provides cheminformatics research support to molecular modelers, medicinal chemists and
            quantitative biologists by integrating robust model builders, property and activity predictors, virtual
            libraries of available chemicals with predicted biological and drug-like properties, and special tools for
            chemical library design. Chembench was initially developed to support researchers in the <a
              href="http://mli.nih.gov/mli/" target="_blank">Molecular Libraries Probe Production Centers Network
            (MLPCN)</a> and the Chemical Synthesis Centers.
          </p>
        </div>
      </section>

      <section class="col-xs-4">
        <s:if test="user == null">
          <h3>Please log in</h3>
        </s:if>
        <s:else>
          <h3>Welcome back</h3>
        </s:else>
        <s:if test="loginFailed=='YES'">
          <div class="alert alert-danger">Username or password incorrect.</div>
        </s:if>
        <s:if test="user==null">
          <s:form action="login" cssClass="form-horizontal" method="post" theme="simple">
            <div class="form-group">
              <label for="username" class="col-xs-4 control-label">Username:</label>

              <div class="col-xs-8">
                <s:textfield name="username" id="username" cssClass="form-control" theme="simple" />
              </div>
            </div>
            <div class="form-group">
              <label for="password" class="col-xs-4 control-label">Password:</label>

              <div class="col-xs-8">
                <s:password name="password" id="password" cssClass="form-control" theme="simple" />
              </div>
            </div>
            <div class="form-group">
              <div class="col-xs-offset-4 col-xs-8">
                <input class="btn btn-primary" value="Log in" type="submit">
              </div>
            </div>
            <div class="form-group">
              <div class="col-xs-offset-4 col-xs-8">
                <s:url var="guestLoginUrl" action="login">
                  <s:param name="username">guest</s:param>
                  <s:param name="ip"><s:property value="ipAddress" /></s:param>
                </s:url>
                Or, <s:a href="%{guestLoginUrl}" cssClass="guest-login">log in as a guest</s:a>
              </div>
            </div>
            <div class="form-group">
              <div class="col-xs-offset-4 col-xs-8">
                <s:a action="forgotPassword">Forgot your password?</s:a><br>
                <s:a action="loadRegistrationPage">Register an account</s:a>
              </div>
            </div>
          </s:form>
        </s:if>

        <s:if test="user != null && !user.userName.isEmpty()">
          <s:if test="user.userName.contains('guest')">
            Logged in as a <b>guest</b>.
          </s:if>
          <s:else>
            Logged in as <b><s:property value="user.userName" /></b>.
          </s:else>
          <s:a action="logout" cssClass="btn btn-primary btn-xs logout-button">Log out</s:a>
        </s:if>

        <h3>Help &amp; Resources</h3>
        <ul class="links-list">
          <li><s:a action="overview" namespace="/help" target="_blank">Chembench Overview</s:a></li>
          <li><s:a action="workflows" namespace="/help" target="_blank">Workflows &amp; Methodology</s:a></li>
        </ul>
        <s:if test="showStatistics!=null || showStatistics=='NO'">
          <h3>Site Stats</h3>
          <dl class="dl-horizontal properties-list">
            <dt>Total visitors</dt>
            <dd><s:property value="visitors" /></dd>

            <dt>Registered users</dt>
            <dd><s:property value="userStats" /></dd>

            <dt>Jobs completed</dt>
            <dd><s:property value="jobStats" /></dd>

            <dt>Compute time used</dt>
            <dd><s:property value="cpuStats" /> years</dd>

            <dt>Current users</dt>
            <dd><s:property value="activeUsers" /></dd>

            <dt>Running jobs</dt>
            <dd><s:property value="runningJobs" /></dd>
          </dl>
        </s:if>
      </section>
    </div>

    <hr>
    <section id="sponsor-section">
      <h4>We thank the following commercial sponsors for their support:</h4>
      <ul id="sponsor-list">
        <li><a href="http://www.chemcomp.com" target="_blank"><img
            src="${pageContext.request.contextPath}/assets/images/sponsors/ccg.jpg" width="114" height="46"
            alt="Chemical Computing Group" class="img-thumbnail"></a></li>
        <li><a href="http://chm.kode-solutions.net/" target="_blank"><img
            src="${pageContext.request.contextPath}/assets/images/sponsors/kode.png" width="150" height="70"
            alt="Kode srl" class="img-thumbnail"></a></li>
        <li><a href="http://www.chemaxon.com" target="_blank"><img
            src="${pageContext.request.contextPath}/assets/images/sponsors/chemaxon.jpg" width="88" height="83"
            alt="ChemAxon" class="img-thumbnail"></a></li>
        <li><a href="http://www.edusoft-lc.com" target="_blank"><img
            src="${pageContext.request.contextPath}/assets/images/sponsors/edusoft.jpg" width="99" height="71"
            alt="eduSoft" class="img-thumbnail"></a></li>
        <li><a href="http://www.sunsetmolecular.com" target="_blank"><img
            src="${pageContext.request.contextPath}/assets/images/sponsors/sunsetmolecular.jpg" width="100" height="100"
            alt="Sunset Molecular" class="img-thumbnail"></a></li>
      </ul>
    </section>
  </div>

  <%@ include file="/jsp/main/footer.jsp" %>
</div>
<%@ include file="/jsp/main/tail.jsp" %>
<script src="${pageContext.request.contextPath}/assets/js/home.js"></script>
</body>
</html>
